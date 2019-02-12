package com.smartbear.soapui.raml.importers;


import com.eviware.soapui.impl.actions.SilentApiImporter;
import com.eviware.soapui.impl.actions.UnsupportedDefinitionException;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.plugins.auto.PluginSilentApiImporter;
import com.smartbear.soapui.raml.AbstractRamlImporter;
import com.smartbear.soapui.raml.RamlUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

@SuppressWarnings("unused")
@PluginSilentApiImporter(formatName = "RAML")
public class SilentRamlApiImporter implements SilentApiImporter {
    @Override
    public boolean acceptsURL(URL url) {
        String urlToLowerCase = url.toString().toLowerCase();
        return urlToLowerCase.endsWith(".raml");
    }

    @Override
    public Collection<Interface> importApi(URL url, WsdlProject wsdlProject) throws UnsupportedDefinitionException {
        AbstractRamlImporter ramlImporter = RamlUtils.createRamlImporter(url.toString(), wsdlProject);
        ramlImporter.setCreateSampleRequests(true);
        RestService restService = ramlImporter.importRaml(url.toString());

        ArrayList<Interface> interfaces = new ArrayList<>();
        interfaces.add(restService);
        return interfaces;
    }
}
