/*
The following schema should be created before this sample is run:

    schema = graph.schema();
    schema.propertyKey("id").Text().single().create();
    schema.vertexLabel("record").partitionKey(\"id\").create();
    schema.propertyKey("name").Text().create();
    schema.propertyKey("createdAt").Timestamp().create();
    schema.propertyKey("yav").Text().create();
    schema.vertexLabel("record").properties("id","name","createdAt","yav").create();
    schema.vertexLabel("record").index("byId").materialized().by("id").add();
* */

//Defines the data input source (a file which is specified via command line arguments)
source = File.csv("schema.csv")//.delimiter(",")

//Rather than sending in "NULL", or "null" which is in the data we transform those values to a null value
// which will be properly handled by the loader. Alternatively we could set a default value here.
source.transform { if (it['createdAt'] in ["NULL", "Null", "null"]) it['createdAt']=null; return it }

//Defines the mapping from input file to graph
load(source).asVertices {
    label "record"
    key "id"
}