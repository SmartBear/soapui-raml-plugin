package com.smartbear.soapui.raml;

import com.eviware.soapui.plugins.PluginAdapter;
import com.eviware.soapui.plugins.PluginConfiguration;

/**
 * Created by ole on 08/06/14.
 */

@PluginConfiguration( groupId = "com.smartbear.soapui.plugins", name = "RAML Plugin", version = "1.3.2",
    autoDetect = true, description = "Provides RAML import/export functionality and an ApiHub API browser",
    infoUrl = "https://github.com/olensmar/soapui-raml-plugin")
public class PluginConfig extends PluginAdapter {
    @Override
    public void initialize() {
        super.initialize();
    }
}
