/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.adapter.gateway.common.rule;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;

/**
 * @author Eric Zhao
 * @since 1.6.0
 */
public class GatewayParamFlowItem{

    /**
     * Should be set when applying to parameter flow rules.
     */
    private Integer index;

    /**
     * Strategy for parsing item (e.g. client IP, arbitrary headers and URL parameters).
     * 从请求中提取参数的策略，目前支持提取来源 IP、Host、任意 Header 和任意 URL 参数四种策略。
     */
    private int parseStrategy;

    /**
     * Field to get (only required for arbitrary headers or URL parameters mode).
     * 若提取策略选择 Header 模式或 URL 参数模式，则需要指定对应的 header 名称或 URL 参数名称。
     */
    private String fieldName;

    /**
     * Matching pattern. If not set, all values will be kept in LRU map.
     * 参数值的匹配模式，只有匹配该模式的请求属性值会纳入统计和流控；若为空则统计该请求属性的所有值。
     */
    private String pattern;

    /**
     * Matching strategy for item value.
     * 参数值的匹配策略，目前支持精确匹配、子串匹配和正则匹配三种策略。
     */
    private int matchStrategy = SentinelGatewayConstants.PARAM_MATCH_STRATEGY_EXACT;

    public Integer getIndex(){
        return index;
    }

    GatewayParamFlowItem setIndex(Integer index){
        this.index = index;
        return this;
    }

    public int getParseStrategy(){
        return parseStrategy;
    }

    public GatewayParamFlowItem setParseStrategy(int parseStrategy){
        this.parseStrategy = parseStrategy;
        return this;
    }

    public String getFieldName(){
        return fieldName;
    }

    public GatewayParamFlowItem setFieldName(String fieldName){
        this.fieldName = fieldName;
        return this;
    }

    public String getPattern(){
        return pattern;
    }

    public GatewayParamFlowItem setPattern(String pattern){
        this.pattern = pattern;
        return this;
    }

    public int getMatchStrategy(){
        return matchStrategy;
    }

    public GatewayParamFlowItem setMatchStrategy(int matchStrategy){
        this.matchStrategy = matchStrategy;
        return this;
    }

    @Override
    public String toString(){
        return "GatewayParamFlowItem{" + "index=" + index + ", parseStrategy=" + parseStrategy + ", fieldName='" + fieldName + '\'' + ", pattern='"
                        + pattern + '\'' + ", matchStrategy=" + matchStrategy + '}';
    }
}
