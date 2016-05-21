package uk.ac.cam.tfc_server.util;

// Constants used in tfc server platform

public class Constants {
// module message types
//
    // FeedPlayer msg_type values
    public static final String FEED_BUS_POSITION = "feed_bus_position"; // e.g. as derived from GTFS bus
    
    // Zone msg_type values which flow on zone.address
    public static final String ZONE_START = "zone_entry_start"; // vehicle entered zone via start line
    public static final String ZONE_COMPLETION = "zone_completion"; // vehicle exitted zone via finish line
    public static final String ZONE_ENTRY = "zone_entry"; // vehicle entered zone not via start line
    public static final String ZONE_EXIT = "zone_exit"; // vehicle exitted zone not via finish line
    public static final String ZONE_UPDATE = "zone_update"; // zone_update msg (all completions so far today)
    public static final String ZONE_INFO = "zone_info"; // zone_info msg (zone details, such as boundary polygon)

    // Manager msg_type values
    public static final String ZONE_SUBSCRIBE = "zone_subscribe"; // request zone to subscribe to feed
    public static final String ZONE_UPDATE_REQUEST = "zone_update_request"; // request zone to publish a ZONE_UPDATE msg
    public static final String ZONE_INFO_REQUEST = "zone_info_request"; // request zone to publish a ZONE_INFO msg

// module constants
    public static final int    ZONE_BUFFER_SIZE = 1000; // max number of Zone Completion messages to be stored in buffer

    // browser socket msg_type values
    public static final String SOCKET_ZONE_CONNECT = "zone_connect"; // msg_type zone_plot.hbs initial connect
    public static final String SOCKET_ZONE_MAP_CONNECT = "zone_map_connect"; // msg_type zone_map.hbs initial connect
    public static final String SOCKET_FEED_CONNECT = "feed_connect"; // msg_type feed.hbs initial connect

    public static String js()
    {
        return "{ \n" +
                  "ZONE_COMPLETION: '"+ZONE_COMPLETION+"',\n" +
                  "ZONE_UPDATE: '"+ZONE_UPDATE+"',\n" +
                  "ZONE_INFO: '"+ZONE_INFO+"',\n" +
                  "SOCKET_ZONE_CONNECT: '"+SOCKET_ZONE_CONNECT+"',\n" +
                  "SOCKET_ZONE_MAP_CONNECT: '"+SOCKET_ZONE_MAP_CONNECT+"',\n" +
                  "SOCKET_FEED_CONNECT: '"+SOCKET_FEED_CONNECT+"'\n" +
            "}";
    }
}
