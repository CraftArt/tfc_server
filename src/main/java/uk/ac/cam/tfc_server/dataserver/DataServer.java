package uk.ac.cam.tfc_server.dataserver;

// *************************************************************************************************
// *************************************************************************************************
// *************************************************************************************************
// DataServer.java
//
// Serves template 'data' http pages with NO realtime socket or eventbus bridge to browser
//
// This is to provide as robust as possible a platform for most people viewing the data
//
// Author: Ian Lewis ijl20@cam.ac.uk
//
// Forms part of the 'tfc_server' next-generation Realtime Intelligent Traffic Analysis system
//
//
// *************************************************************************************************
// *************************************************************************************************
// *************************************************************************************************

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.DeploymentOptions;

import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpMethod;

import io.vertx.core.file.FileSystem;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.buffer.Buffer;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

// vertx web, service proxy, sockjs eventbus bridge
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.handler.sockjs.SockJSSocket;

// handlebars for static .hbs web template files
import io.vertx.ext.web.templ.HandlebarsTemplateEngine;

import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

// other tfc_server classes
import uk.ac.cam.tfc_server.util.Constants;

public class DataServer extends AbstractVerticle {

    private Integer HTTP_PORT; // from config()

    private String EB_SYSTEM_STATUS; // from config()
    private String EB_MANAGER; // from config()
    private String MODULE_NAME; // from config()
    private String MODULE_ID; // from config()
    private String WEBROOT; // from config()
    
    private String BASE_URI; // used as template parameter for web pages, built from config()
    
    private final int SYSTEM_STATUS_PERIOD = 10000; // publish status heartbeat every 10 s
    private final int SYSTEM_STATUS_AMBER_SECONDS = 15;
    private final int SYSTEM_STATUS_RED_SECONDS = 25;

    // Vertx event bus
    private EventBus eb = null; // at least for system_status messages, not for the browser

    @Override
    public void start(Future<Void> fut) throws Exception
    {

    // Get src/main/conf/tfc_server.conf config values for module
    if (!get_config())
        {
            System.err.println("DataServer: problem loading config");
            vertx.close();
            return;
        }

    System.out.println("DataServer starting as "+MODULE_NAME+"."+MODULE_ID+
                       " on port "+HTTP_PORT );

    BASE_URI = MODULE_NAME; // typically 'dataserver'

    eb = vertx.eventBus();

    // send periodic "system_status" messages
    init_system_status();
    
    // *************************************************************************************
    // *************************************************************************************
    // *********** Start DataServer web server (incl Socket and EventBus Bridge)      ************
    // *************************************************************************************
    // *************************************************************************************
    HttpServer http_server = vertx.createHttpServer();

    Router router = Router.router(vertx);

    // ********************************
    // create handler for embedded page
    // ********************************

    router.route("/"+BASE_URI+"/home").handler( routingContext -> {

        HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "text/html");

        response.end("<h1>DataServer."+MODULE_ID+"</h1><p>Vertx-Web!</p>");
    });

    // **************************************
    // **************************************
    // create handlers for template pages
    // **************************************
    // **************************************

    final HandlebarsTemplateEngine template_engine = HandlebarsTemplateEngine.create();
    
    router.route(HttpMethod.GET, "/"+BASE_URI+"/plot/zone/:zoneid").handler( ctx -> {
            serve_plot_zone(ctx, ctx.request().getParam("zoneid"), template_engine);
        });
            
    // ********************************
    // create handler for static pages
    // ********************************

    StaticHandler static_handler = StaticHandler.create();
    static_handler.setWebRoot(WEBROOT);
    static_handler.setCachingEnabled(false);
    router.route(HttpMethod.GET, "/static/*").handler( static_handler );

    System.out.println("DataServer."+MODULE_ID+" static handler using "+WEBROOT);
    
    // ********************************
    // connect router to http_server
    // ********************************

    http_server.requestHandler(router::accept).listen(HTTP_PORT);

  } // end start()

    // *******************************************************************************
    // *******************************************************************************
    // *******************************************************************************
    
    // Set periodic timer to broadcast "system UP" status messages to EB_SYSTEM_STATUS address
    private void init_system_status()
    {
    vertx.setPeriodic(SYSTEM_STATUS_PERIOD, id -> {
      eb.publish(EB_SYSTEM_STATUS,
                 "{ \"module_name\": \""+MODULE_NAME+"\"," +
                   "\"module_id\": \""+MODULE_ID+"\"," +
                   "\"status\": \"UP\"," +
                   "\"status_amber_seconds\": "+String.valueOf( SYSTEM_STATUS_AMBER_SECONDS ) + "," +
                   "\"status_red_seconds\": "+String.valueOf( SYSTEM_STATUS_RED_SECONDS ) +
                 "}" );
      });
    }

    // Serve the templates/data_plot.hbs web page
    private void serve_plot_zone(RoutingContext ctx, String zone_id, HandlebarsTemplateEngine engine)
    {
        System.out.println("DataServer."+MODULE_ID+": serving plot.hbs for "+zone_id);
            
        if (zone_id == null)
        {
            ctx.response().setStatusCode(400).end();
        }
        else
        {
            System.out.println("DataServer."+MODULE_ID+": reading file");

            // read the file containing the data
            vertx.fileSystem().readFile("/home/ijl20/tfc_server_data/data_zone/2016/06/13/madingley_road_in_2016-06-13.txt", fileres -> {
                    if (fileres.succeeded()) {

                        // successful file read, so populate page data and return page
                        
                        System.out.println("DataServer."+MODULE_ID+": read file");

                        System.out.println("file starts with "+fileres.result().toString().substring(0,100));

                        ctx.put("config_zone_id",zone_id); // pass zone_id from URL into template var

                        // Convert file contents to valid JSON
                        // File starts as JSON objects separated by newlines
                        
                        String plot_data = fileres.result().toString();

                        // replace newlines with commas
                        plot_data = plot_data.replace("\n",",");

                        //remove trailing comma
                        plot_data = plot_data.substring(0,plot_data.length()-1);

                        // wrap with [] and we have a JSON array containing JSON objects...
                        plot_data = "["+plot_data+"]";

                        ctx.put("config_plot_data", plot_data);

                        engine.render(ctx, "templates/data_plot.hbs", res -> {
                                if (res.succeeded())
                                {
                                    ctx.response().end(res.result());
                                }
                                else
                                {
                                    ctx.fail(res.cause());
                                }
                            });
                    } else {
                        ctx.fail(fileres.cause());
                    }
            });
        }
    }
    
    // Load initialization global constants defining this module from config()
    private boolean get_config()
    {
        // config() values needed by all TFC modules are:
        // module.name e.g. "dataserver"
        // module.id e.g. "A"
        // eb.system_status - String eventbus address for system status messages
        // eb.manager - evenbus address to subscribe to for system management messages

        MODULE_NAME = config().getString("module.name"); // "dataserver"
        if (MODULE_NAME==null)
            {
                System.err.println("DataServer: no module.name in config()");
                return false;
            }
        
        MODULE_ID = config().getString("module.id"); // A, B, ...
        if (MODULE_ID==null)
            {
                System.err.println("DataServer: no module.id in config()");
                return false;
            }

        // common system status reporting address, e.g. for UP messages
        // picked up by Console
        EB_SYSTEM_STATUS = config().getString("eb.system_status");
        if (EB_SYSTEM_STATUS==null)
            {
                System.err.println("DataServer: no eb.system_status in config()");
                return false;
            }

        // system control address - commands are broadcast on this
        EB_MANAGER = config().getString("eb.manager");
        if (EB_MANAGER==null)
            {
                System.err.println("DataServer: no eb.manager in config()");
                return false;
            }

        // port for user browser access to this DataServer
        HTTP_PORT = config().getInteger(MODULE_NAME+".http.port");
        if (HTTP_PORT==null)
            {
                System.err.println("DataServer: no "+MODULE_NAME+".http.port in config()");
                return false;
            }

        // where the built-in webserver will find static files
        WEBROOT = config().getString(MODULE_NAME+".webroot");
        //debug we should properly test for bad config

        return true;
    }

} // end class DataServer
