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
package com.alibaba.csp.sentinel.transport.init;

import com.alibaba.csp.sentinel.command.CommandCenterProvider;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.init.InitOrder;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.transport.CommandCenter;

/**
 * 使用{@link CommandCenterProvider}获取对应的{@link CommandCenter}实现，依次执行beforeStart和start方法，以启动服务。
 * 即只要加载了sentinel-transport-common模块并通过SPI提供CommandCenter的实现，便会在InitFunc被调用时启动服务。
 *
 * @see CommandCenter
 * @see CommandCenterProvider
 * @author Eric Zhao
 */
@InitOrder(-1)
public class CommandCenterInitFunc implements InitFunc{

    @Override
    public void init() throws Exception{
        CommandCenter commandCenter = CommandCenterProvider.getCommandCenter();

        if (commandCenter == null){
            RecordLog.warn("[CommandCenterInitFunc] Cannot resolve CommandCenter");
            return;
        }

        commandCenter.beforeStart();
        commandCenter.start();
        RecordLog.info("[CommandCenterInit] Starting command center: " + commandCenter.getClass().getCanonicalName());
    }
}
