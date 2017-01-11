

//This example works with the following command line configuration:
// java -Djavax.net.ssl.trustStore=<TRUSTSTORE_PATH> -Djavax.net.ssl.trustStorePassword=<PASSWORD> -Djavax.net.ssl.keyStore=<KEYSTORE_PATH> \
// -Djavax.net.ssl.keyStorePassword=<PASSWORD> -jar loader.jar -kerberos true -sasl dsename -graph new -address localhost ssl.groovy

//Configures the data loader to create the schema
config preparation: true, create_schema: true, load_new: true
config load_edge_threads: 2, load_vertex_threads: 2, batch_size: 10

//Defines the data input source (a file which is specified via command line arguments)
source = File.json('github_events10.json');

//Defines the mapping from input file to graph
eventMapper = {
    labelField "type"
    key "id"
    outV "actor",  {
        label "User"
        key "id"
    }
    outV "repo", {
        label "Repository"
        key "id"
    }
    ignore "payload"
    ignore "public"
    ignore "org"
}

//Specifies what data source to load using which mapper
load(source).asVertices(eventMapper)