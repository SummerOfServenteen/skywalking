/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;
import org.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;
import org.skywalking.apm.util.ConfigInitializer;
import org.skywalking.apm.util.StringUtil;

/**
 * The <code>SnifferConfigInitializer</code> initializes all configs in several way.
 *
 * @author wusheng
 * @see {@link #initialize()}, to learn more about how to initialzie.
 */
public class SnifferConfigInitializer {
    private static final ILog logger = LogManager.getLogger(SnifferConfigInitializer.class);
    private static String CONFIG_FILE_NAME = "/sky-walking.config";

    /**
     * Try to locate config file, named {@link #CONFIG_FILE_NAME}, in following order:
     * 1. Path from SystemProperty. {@link #loadConfigBySystemProperty()}
     * 2. class path.
     * 3. Path, where agent is. {@link #loadConfigFromAgentFolder()}
     * <p>
     * If no found in any path, agent is still going to run in default config, {@link Config},
     * but in initialization steps, these following configs must be set, by config file or system properties:
     * <p>
     * 1. applicationCode. "-DapplicationCode=" or  {@link Config.Agent#APPLICATION_CODE}
     * 2. servers. "-Dservers=" or  {@link Config.Collector#SERVERS}
     */
    public static void initialize() throws ConfigNotFoundException, AgentPackageNotFoundException {
        InputStream configFileStream;

        configFileStream = loadConfigFromAgentFolder();

        try {
            Properties properties = new Properties();
            properties.load(configFileStream);
            ConfigInitializer.initialize(properties, Config.class);
        } catch (Exception e) {
            logger.error("Failed to read the config file, sky-walking is going to run in default config.", e);
        }

        String applicationCode = System.getProperty("applicationCode");
        if (!StringUtil.isEmpty(applicationCode)) {
            Config.Agent.APPLICATION_CODE = applicationCode;
        }
        String servers = System.getProperty("servers");
        if (!StringUtil.isEmpty(servers)) {
            Config.Collector.SERVERS = servers;
        }

        if (StringUtil.isEmpty(Config.Agent.APPLICATION_CODE)) {
            throw new ExceptionInInitializerError("'-DapplicationCode=' is missing.");
        }
        if (StringUtil.isEmpty(Config.Collector.SERVERS)) {
            throw new ExceptionInInitializerError("'-Dservers=' is missing.");
        }
    }

    /**
     * Load the config file by the path, which is provided by system property, usually with a "-Dconfig=" arg.
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    private static InputStream loadConfigBySystemProperty() {
        String config = System.getProperty("config");
        if (StringUtil.isEmpty(config)) {
            return null;
        }
        File configFile = new File(config);
        if (configFile.exists() && configFile.isDirectory()) {
            logger.info("check {} in path {}, according system property.", CONFIG_FILE_NAME, config);
            configFile = new File(config, CONFIG_FILE_NAME);
        }

        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("found   {}, according system property.", configFile.getAbsolutePath());
                return new FileInputStream(configFile);
            } catch (FileNotFoundException e) {
                logger.error(e, "Fail to load {} , according system property.", config);
            }
        }

        logger.info("No {}  found, according system property.", config);
        return null;
    }

    /**
     * Load the config file, where the agent jar is.
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    private static InputStream loadConfigFromAgentFolder() throws AgentPackageNotFoundException, ConfigNotFoundException {
        File configFile = new File(AgentPackagePath.getPath(), CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            try {
                logger.info("{} file found in agent folder.", CONFIG_FILE_NAME);
                return new FileInputStream(configFile);
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Fail to load agent.config", e);
            }
        }
        throw new ConfigNotFoundException("Fail to load agent.config");
    }
}
