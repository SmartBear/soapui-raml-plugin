package com.smartbear.soapui.raml.actions;

import com.eviware.soapui.analytics.Analytics;
import com.eviware.soapui.impl.WorkspaceImpl;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.plugins.auto.PluginImportMethod;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.dialogs.XProgressDialog;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldListener;
import com.eviware.x.form.support.ADialogBuilder;
import com.eviware.x.form.support.AField;
import com.eviware.x.form.support.AForm;

import java.io.File;

/**
 * Created by ole on 21/06/14.
 */

@PluginImportMethod( label = "RAML Definition (REST)")
public class CreateRamlProjectAction extends AbstractSoapUIAction<WorkspaceImpl> {

    private XFormDialog dialog;

    public CreateRamlProjectAction()
    {
        super( "Create RAML Project", "Creates a new SoapUI Project from a RAML definition");
    }

    @Override
    public void perform(WorkspaceImpl workspace, Object o) {
        // initialize form
        if (dialog == null) {
            dialog = ADialogBuilder.buildDialog(Form.class);
            dialog.setBooleanValue( Form.CREATE_REQUESTS, true );
            dialog.getFormField( Form.RAML_URL ).addFormFieldListener( new XFormFieldListener() {
                @Override
                public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                   initProjectName( newValue );
                }
            });
        } else {
            dialog.setValue(Form.RAML_URL, "");
            dialog.setValue(Form.PROJECT_NAME, "");
        }

        WsdlProject project = null;

        while (dialog.show()) {
            try {
                // get the specified URL
                String url = dialog.getValue(Form.RAML_URL).trim();
                if (StringUtils.hasContent(url)) {
                    project = workspace.createProject(dialog.getValue(Form.PROJECT_NAME));
                    String expUrl = PathUtils.expandPath(url, project);

                    // if this is a file - convert it to a file URL
                    if (new File(expUrl).exists())
                        expUrl = new File(expUrl).toURI().toURL().toString();

                    XProgressDialog dlg = UISupport.getDialogs().createProgressDialog("Importing API", 0, "", false);
                    dlg.run(new RamlImporterWorker(expUrl, project, dialog));

                    Analytics.trackAction("CreateRAMLProject");

                    break;
                }
            } catch (Exception ex) {
                UISupport.showErrorMessage(ex);
            }
        }

        if( project != null && project.getInterfaceCount() == 0 )
            workspace.removeProject( project );
    }

    public void initProjectName(String newValue) {
        if (StringUtils.isNullOrEmpty(dialog.getValue(Form.PROJECT_NAME)) && StringUtils.hasContent(newValue)) {
            int ix = newValue.lastIndexOf('.');
            if (ix > 0) {
                newValue = newValue.substring(0, ix);
            }

            ix = newValue.lastIndexOf('/');
            if (ix == -1) {
                ix = newValue.lastIndexOf('\\');
            }

            if (ix != -1) {
                dialog.setValue(Form.PROJECT_NAME, newValue.substring(ix + 1));
            }
        }
    }

    @AForm(name = "Create RAML Project", description = "Creates a Project from the specified RAML definition")
    public interface Form {
        @AField(name = "Project Name", description = "Name of the project", type = AField.AFieldType.STRING)
        public final static String PROJECT_NAME = "Project Name";

        @AField(name = "RAML Definition", description = "Location or URL of RAML definition", type = AField.AFieldType.FILE)
        public final static String RAML_URL = "RAML Definition";

        @AField(name = "Create Requests", description = "Create sample requests for imported methods", type = AField.AFieldType.BOOLEAN)
        public final static String CREATE_REQUESTS = "Create Requests";

        @AField( name = "Generate Virt", description = "Generate a REST Virt from the RAML definition", type = AField.AFieldType.BOOLEAN )
        public final static String GENERATE_MOCK = "Generate Virt";

        @AField( name = "Generate TestSuite", description = "Generate a skeleton TestSuite for the created REST API", type = AField.AFieldType.BOOLEAN )
        public final static String GENERATE_TESTSUITE = "Generate TestSuite";
    }
}
