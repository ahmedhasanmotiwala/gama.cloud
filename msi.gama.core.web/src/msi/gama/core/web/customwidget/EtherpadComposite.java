package msi.gama.core.web.customwidget;
 
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.rap.json.JsonObject;
import org.eclipse.rap.json.JsonValue;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.client.service.JavaScriptLoader;
import org.eclipse.rap.rwt.remote.AbstractOperationHandler;
import org.eclipse.rap.rwt.remote.Connection;
import org.eclipse.rap.rwt.remote.OperationHandler;
import org.eclipse.rap.rwt.remote.RemoteObject;
import org.eclipse.rap.rwt.service.ResourceManager;
import org.eclipse.rap.rwt.service.UISession;
import org.eclipse.rap.rwt.widgets.WidgetUtil;
import org.eclipse.swt.widgets.Composite;

import msi.gama.runtime.IScope;
import ummisco.gama.ui.utils.WorkbenchHelper;
 
public class EtherpadComposite extends Composite {
 
 
   private static final long serialVersionUID = -8590973451146216709L;
 
   private RemoteObject remoteObject;
 
 
   // The directory containing the file js, css.
   private static final String REAL_RESOURCE_PATH = "etherpadjsresources";
 
   private static final String REGISTER_PATH = "etherpadjs";
 
   private static final String REMOTE_TYPE = "o7planning.EtherpadComposite";
 
   private final String[] FILENAMES = {  "etherpadjs.css",  "etherpadjs.js" ,"load-css-file.js" , "rap-handler.js"};
 
   private final OperationHandler operationHandler = new AbstractOperationHandler() {
 
       @Override
       public void handleSet(JsonObject properties) {
           System.out.println("##### handleSet ..:");
           JsonValue textValue = properties.get("text");
           if (textValue != null) {
               // text = textValue.asString();
           }
       }
 
       @Override
       public void handleCall(String method, JsonObject parameters) {
           System.out.println("##### handleCall ..:" + method);
       }
 
       @Override
       public void handleNotify(String event, JsonObject properties) {
           System.out.println("##### handleNotify ..:" + event);
           if (event.equals("dirty")) {
           }
       }
 
   };
 
   /**
    * Create the composite.
    *
    * @param parent
    * @param style
    */
   public EtherpadComposite(Composite parent, int style) {
       super(parent, style);
 
       System.out.println("Ceci est un test de composite!");
       // Note: Catching error when viewed on WindowBuilder
       try {
           registerResources();
           loadJavaScript();

			
           Connection connection = RWT.getUISession().getConnection();
           remoteObject = connection.createRemoteObject(REMOTE_TYPE);
           remoteObject.setHandler(operationHandler);
 
           //
           remoteObject.set("parent", WidgetUtil.getId(this));
 
       } catch (Exception e) {
           e.printStackTrace();
           // throw new RuntimeException(e);
       }
   }
    
 
   @Override
   public void dispose()  {
       super.dispose();        
 
       // Call destroy() function in rap-handler.js
       remoteObject.destroy();
   }
 
 
   // Load the js files required at Client.
   private void loadJavaScript() {
       JavaScriptLoader jsLoader = RWT.getClient().getService(
               JavaScriptLoader.class);
       ResourceManager resourceManager = RWT.getResourceManager();
 
       // Load file logjs.js into page

//       jsLoader.require(resourceManager.getLocation(REGISTER_PATH + "/"
//               + "etherpad.jquery.json"));
       

//       jsLoader.require(resourceManager.getLocation(REGISTER_PATH + "/"
//               + "etherpad.js"));

//       jsLoader.require(resourceManager.getLocation(REGISTER_PATH + "/"
//               + "jquery-latest.min.js"));

       jsLoader.require(resourceManager.getLocation(REGISTER_PATH + "/"
               + "etherpadjs.js"));

       // Load file load-css-file.js into page
    
       jsLoader.require(resourceManager.getLocation(REGISTER_PATH + "/"
               + "load-css-file.js"));

       // Load file rap-handler.js into page.
 
        jsLoader.require(resourceManager.getLocation(REGISTER_PATH + "/"
                  + "rap-handler.js"));

   }
 
   private void registerResources() throws IOException {
       ResourceManager resourceManager = RWT.getResourceManager();
 
       for (String fileName : FILENAMES) {
 
           // After registering, you can access on your browser:
            
           // (http://localhost:port/rwt-resources/logjs/abc.js )
           // logjs/abc.js            
           String path = REGISTER_PATH + "/" + fileName;
 
            // Check this resource has been registered yet.
           boolean isRegistered = resourceManager.isRegistered(path);
 
           if (!isRegistered) {
               ClassLoader classLoader = EtherpadComposite.class.getClassLoader();
               // Real Path (in src)
                
               // logjsresources/abc.js
               String realPath = REAL_RESOURCE_PATH + "/" + fileName;
 
               InputStream inputStream = classLoader
                       .getResourceAsStream(realPath);
               if (inputStream == null) {
                   throw new IOException("File not found " + realPath);
               }
               try {
                   // Register resource                    
                   resourceManager.register(path, inputStream);
               } finally {
                   inputStream.close();
               }
           }
       }
   }
 
   @Override
   protected void checkSubclass() {
   }
 
   public void appendWarn(String text) {
//       System.out.println("appendWarn");
       JsonObject obj= new JsonObject();
       obj.add("text", text);
       this.remoteObject.call("appendWarn", obj);
   }
    
   public void appendErr(String text) {
//       System.out.println("appendErr");
       JsonObject obj= new JsonObject();
       obj.add("text", text);       
       this.remoteObject.call("appendErr", obj);
   }
    
   public void appendInfo(final IScope scope, String text) {
////       System.out.println("appendInfo");
//       JsonObject obj= new JsonObject();
//       obj.add("text", text);
//       this.remoteObject.call("appendInfo", obj);

//		final Runnable runnable = new Runnable() {
//		  public void run() {
		final String uid=WorkbenchHelper.UISession.get(scope.getExperiment().getSpecies().getExperimentScope());

		    UISession uiSession = RWT.getUISession(WorkbenchHelper.getDisplay(uid));
		    uiSession.exec( new Runnable() {
		      public void run() {

		          JsonObject obj= new JsonObject();
		          obj.add("text", text);
		          remoteObject.call("appendInfo", obj);	 
		        
		      }
		    } );
//		  }
//		};
//		runnable.run();
//		new Thread( runnable ).start();
   }
    
   public void clearAll() {
//       System.out.println("clearAll");
       UISession uiSession = RWT.getUISession(WorkbenchHelper.getDisplay("admin"));
	    uiSession.exec( new Runnable() {
	      public void run() {

	          remoteObject.call("clearAll", new JsonObject());
	        
	      }
	    } );
   }
}