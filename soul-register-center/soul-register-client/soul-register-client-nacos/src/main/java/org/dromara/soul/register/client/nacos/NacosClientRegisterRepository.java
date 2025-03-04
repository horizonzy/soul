/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.soul.register.client.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dromara.soul.common.constant.Constants;
import org.dromara.soul.common.utils.GsonUtils;
import org.dromara.soul.register.client.api.SoulClientRegisterRepository;
import org.dromara.soul.register.common.config.SoulRegisterCenterConfig;
import org.dromara.soul.register.common.dto.MetaDataRegisterDTO;
import org.dromara.soul.register.common.dto.URIRegisterDTO;
import org.dromara.soul.register.common.path.RegisterPathConstants;
import org.dromara.soul.spi.Join;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * nacos register center client.
 *
 * @author lw1243925457
 */
@Join
@Slf4j
public class NacosClientRegisterRepository implements SoulClientRegisterRepository {

    private String defaultGroup = "default_group";

    private ConfigService configService;

    private NamingService namingService;

    private final ConcurrentLinkedQueue<String> metadataCache = new ConcurrentLinkedQueue<>();

    private boolean registerService;

    @SneakyThrows
    @Override
    public void init(final SoulRegisterCenterConfig config) {
        String serverAddr = config.getServerLists();
        Properties properties = config.getProps();

        Properties nacosProperties = new Properties();
        nacosProperties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        String nameSpace = "nacosNameSpace";
        nacosProperties.put(PropertyKeyConst.NAMESPACE, properties.getProperty(nameSpace));
        this.configService = ConfigFactory.createConfigService(nacosProperties);
        this.namingService = NamingFactory.createNamingService(nacosProperties);
    }

    @Override
    public void close() {
    }

    @Override
    public void persistInterface(final MetaDataRegisterDTO metadata) {
        String rpcType = metadata.getRpcType();
        String contextPath = metadata.getContextPath().substring(1);
        String host = metadata.getHost();
        int port = metadata.getPort();

        registerService(rpcType, contextPath, host, port, metadata);
        registerConfig(rpcType, contextPath, metadata);
    }

    @SneakyThrows
    private synchronized void registerService(final String rpcType, final String contextPath, final String host,
                                              final int port, final MetaDataRegisterDTO metadata) {
        if (registerService) {
            return;
        }
        registerService = true;

        Instance instance = new Instance();
        instance.setEphemeral(true);
        instance.setIp(host);
        instance.setPort(port);

        Map<String, String> metadataMap = new HashMap<>();
        metadataMap.put(Constants.CONTEXT_PATH, contextPath);
        metadataMap.put("uriMetadata", GsonUtils.getInstance().toJson(URIRegisterDTO.transForm(metadata)));
        instance.setMetadata(metadataMap);

        String serviceName = RegisterPathConstants.buildServiceInstancePath(rpcType);
        namingService.registerInstance(serviceName, instance);

        log.info("register service success: {}", serviceName);
    }

    @SneakyThrows
    private synchronized void registerConfig(final String rpcType, final String contextPath,
                                             final MetaDataRegisterDTO metadata) {
        metadataCache.add(GsonUtils.getInstance().toJson(metadata));

        String configName = RegisterPathConstants.buildServiceConfigPath(rpcType, contextPath);
        configService.publishConfig(configName, defaultGroup, GsonUtils.getInstance().toJson(metadataCache));

        log.info("register metadata success: {}", metadata.getRuleName());
    }
}
