/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.flow;

import java.util.Collection;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.function.Function;

/**
 * Rule checker for flow control rules.
 *
 * @author Eric Zhao
 */
public class FlowRuleChecker{

    /**
     *
     * FlowSlot 会调用此方法判断
     *
     * @param ruleProvider
     * @param resource
     * @param context
     * @param node
     * @param count
     * @param prioritized
     * @throws BlockException
     */
    public void checkFlow(
                    Function<String, Collection<FlowRule>> ruleProvider,
                    ResourceWrapper resource,
                    Context context,
                    DefaultNode node,
                    int count,
                    boolean prioritized) throws BlockException{
        if (ruleProvider == null || resource == null){
            return;
        }
        Collection<FlowRule> rules = ruleProvider.apply(resource.getName());
        if (rules != null){
            for (FlowRule rule : rules){
                if (!canPassCheck(rule, context, node, count, prioritized)){
                    throw new FlowException(rule.getLimitApp(), rule);
                }
            }
        }
    }

    public boolean canPassCheck(/* @NonNull */ FlowRule rule,Context context,DefaultNode node,int acquireCount){
        return canPassCheck(rule, context, node, acquireCount, false);
    }

    public boolean canPassCheck(/* @NonNull */ FlowRule rule,Context context,DefaultNode node,int acquireCount,boolean prioritized){
        // 来源app限制
        String limitApp = rule.getLimitApp();
        if (limitApp == null){
            return true;
        }

        // 流控规则 是否是集群模式
        if (rule.isClusterMode()){
            return passClusterCheck(rule, context, node, acquireCount, prioritized);
        }

        //不是集群模式  则走本地模式
        return passLocalCheck(rule, context, node, acquireCount, prioritized);
    }

    private static boolean passLocalCheck(FlowRule rule,Context context,DefaultNode node,int acquireCount,boolean prioritized){
        Node selectedNode = selectNodeByRequesterAndStrategy(rule, context, node);
        if (selectedNode == null){
            return true;
        }

        return rule.getRater().canPass(selectedNode, acquireCount, prioritized);
    }

    static Node selectReferenceNode(FlowRule rule,Context context,DefaultNode node){
        String refResource = rule.getRefResource();
        int strategy = rule.getStrategy();

        if (StringUtil.isEmpty(refResource)){
            return null;
        }

        if (strategy == RuleConstant.STRATEGY_RELATE){
            return ClusterBuilderSlot.getClusterNode(refResource);
        }

        if (strategy == RuleConstant.STRATEGY_CHAIN){
            if (!refResource.equals(context.getName())){
                return null;
            }
            return node;
        }
        // No node.
        return null;
    }

    private static boolean filterOrigin(String origin){
        // Origin cannot be `default` or `other`.
        return !RuleConstant.LIMIT_APP_DEFAULT.equals(origin) && !RuleConstant.LIMIT_APP_OTHER.equals(origin);
    }

    static Node selectNodeByRequesterAndStrategy(/* @NonNull */ FlowRule rule,Context context,DefaultNode node){
        // The limit app should not be empty.
        String limitApp = rule.getLimitApp();
        int strategy = rule.getStrategy();
        String origin = context.getOrigin();

        if (limitApp.equals(origin) && filterOrigin(origin)){
            if (strategy == RuleConstant.STRATEGY_DIRECT){
                // Matches limit origin, return origin statistic node.
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        }else if (RuleConstant.LIMIT_APP_DEFAULT.equals(limitApp)){
            if (strategy == RuleConstant.STRATEGY_DIRECT){
                // Return the cluster node.
                return node.getClusterNode();
            }

            return selectReferenceNode(rule, context, node);
        }else if (RuleConstant.LIMIT_APP_OTHER.equals(limitApp) && FlowRuleManager.isOtherOrigin(origin, rule.getResource())){
            if (strategy == RuleConstant.STRATEGY_DIRECT){
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        }

        return null;
    }

    /**
     * 集群模式的流控规则校验；用SPI机制获取 TokenService
     * 
     * @param rule
     *            当前流控规则
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean passClusterCheck(FlowRule rule,Context context,DefaultNode node,int acquireCount,boolean prioritized){
        try{

            /**
             * 根据当前实例的角色获取集群tokenService
             * 1、client 模式取默认的 DefaultClusterTokenClient
             * 2、server 模式取默认的 DefaultEmbeddedTokenServer -- 嵌入模式下的集群
             */
            TokenService clusterService = pickClusterService();
            if (clusterService == null){
                // 集群模式 但是又拿不到tokenService，则走本地模式（如果不走本地模式，则直接通过）
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            }

            long flowId = rule.getClusterConfig().getFlowId();

            TokenResult result = clusterService.requestToken(flowId, acquireCount, prioritized);

            return applyTokenResult(result, rule, context, node, acquireCount, prioritized);
            // If client is absent, then fallback to local mode.
        }catch (Throwable ex){
            RecordLog.warn("[FlowRuleChecker] Request cluster token unexpected failed", ex);
        }
        // Fallback to local flow control when token client or server for this rule is not available.
        // If fallback is not enabled, then directly pass.
        return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
    }

    private static boolean fallbackToLocalOrPass(FlowRule rule,Context context,DefaultNode node,int acquireCount,boolean prioritized){
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()){
            return passLocalCheck(rule, context, node, acquireCount, prioritized);
        }else{
            // The rule won't be activated, just pass.
            return true;
        }
    }

    private static TokenService pickClusterService(){
        if (ClusterStateManager.isClient()){
            //com.alibaba.csp.sentinel.cluster.client.DefaultClusterTokenClient
            return TokenClientProvider.getClient();
        }
        if (ClusterStateManager.isServer()){
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    private static boolean applyTokenResult(
                    /* @NonNull */ TokenResult result,
                    FlowRule rule,
                    Context context,
                    DefaultNode node,
                    int acquireCount,
                    boolean prioritized){
        switch (result.getStatus()) {
            case TokenResultStatus.OK:
                return true;
            case TokenResultStatus.SHOULD_WAIT:
                // Wait for next tick.
                try{
                    Thread.sleep(result.getWaitInMs());
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
                return true;
            case TokenResultStatus.NO_RULE_EXISTS:
            case TokenResultStatus.BAD_REQUEST:
            case TokenResultStatus.FAIL:
            case TokenResultStatus.TOO_MANY_REQUEST:
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            case TokenResultStatus.BLOCKED:
            default:
                return false;
        }
    }
}