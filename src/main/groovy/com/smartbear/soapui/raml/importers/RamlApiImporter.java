package com.smartbear.soapui.raml.importers;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.plugins.ApiImporter;
import com.eviware.soapui.plugins.PluginApiImporter;
import com.smartbear.soapui.raml.actions.ImportRamlAction;

import java.util.ArrayList;
import java.util.List;

@PluginApiImporter( label = "RAML" )
public class RamlApiImporter implements ApiImporter {
    @Override
    public List<Interface> importApis(Project project) {

        List<Interface> result = new ArrayList<>();
        int cnt = project.getInterfaceCount();

        ImportRamlAction importRamlAction = new ImportRamlAction();
        importRamlAction.perform( (WsdlProject)project, null );

        for( int c = cnt; c < project.getInterfaceCount(); c++)
        {
            result.add( project.getInterfaceAt( c ));
        }

        return result;
    }
}
