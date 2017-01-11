# dse-graph-NorthWind-database - work in progress...

#Pre-requisites

-Install or upgrade to DSE 5.0.5
-Download DSE Graph Loader
-Download DSE Studio (or install the dse-demos package)

##Start DSE Studio
Reference: http://docs.datastax.com/en/latest-dse/datastax_enterprise/graph/QuickStartStudio.html?hl=studio

You can unzip the Studio download into a location of your choice.
For example:
```
tar -xzvf datastax-studio-1.0.2.tar.gz
sudo mv datastax-studio-1.0.2 /opt
cd /opt/datastax-studio-1.0.2/bin
nohup ./datastax-studio-1.0.2/bin/server.sh &
```
You'll find Studio running on port 9091

##Install DSE Graph Loader
Reference: http://docs.datastax.com/en/latest-dse/datastax_enterprise/graph/dgl/graphloaderTOC.html?hl=graphloader

You can unzip the Graphloader download into a location of your choice.
For example:
```
tar -xzvf dse-graph-loader-5.0.5.tar.gz
sudo mv dse-graph-loader-5.0.5 /opt
```
>You can now set an environment variable to point to this location in your graphloader commands, for example like this
```
LOADER_HOME=/opt/dse-graph-loader-5.0.5 export LOADER_HOME
$LOADER_HOME/graphloader ./northwind-map.groovy  -graph testGRYO -address localhost -dryrun false
```


#Get Data
Reference: http://docs.datastax.com/en/latest-dse/datastax_enterprise/graph/dgl/dglGRYO.html?hl=kryo

Download the Northwind database data file from https://github.com/dkuppitz/sql2gremlin/blob/master/assets/northwind.kryo

#Create a mapping file

Create northwind-mapping.groovy:
```
//Configures the data loader to create the schema
config create_schema: true, load_new: true

def inputpath = '/home/dse/dse_dev/dse-graph-Northwind-loader/';
def inputfile = inputpath + 'northwind.kryo';

//Defines the data input source (a file which is specified via command line arguments)
source = Graph.file(inputfile).gryo()

//Specifies what data source to load using which mapper
load(source.vertices()).asVertices {
    labelField "~label"
    key "~id", "id"
}

load(source.edges()).asEdges {
    labelField "~label"
    outV "outV", {
        labelField "~label"
        key "~id", "id"
    }
    inV "inV", {
        labelField "~label"
        key "~id", "id"
    }
}
```
#Load The Data

Delete an old one if there is one:
```
gremlin> :remote config alias g testGRYO.g
gremlin> schema.config().option('graph.schema_mode').set('Development')
gremlin> schema.clear()
```


Load data - create graph called testGRYO - change -dryrun to false when ready to load:
```
LOADER_HOME=/opt/dse-graph-loader-5.0.5 export LOADER_HOME
cd /home/dse/dse_dev/dse-graph-Northwind-loader
$LOADER_HOME/graphloader ./northwind-map.groovy  -graph testGRYO -address localhost -dryrun false
...
2017-01-10 13:36:22 INFO  Reporter:92 - ADD Request for 0 vertices 4077 edges 0 properties 0 anonymous
2017-01-10 13:36:22 INFO  Reporter:97 - Current total additions: 3209 vertices 6177 edges 14554 properties 0 anonymous
2017-01-10 13:36:22 INFO  Reporter:99 - 23940 total elements written
```

#Create Notebook

Create a new notebook - call it Northwind, connect to the testGRYO graph database
Test its all in there:
```
g.V().count() = 3209
```
sample queries on the data:
http://sql2gremlin.com/
e.g. g.V().hasLabel("category").valueMap("name", "description")

also refer to https://github.com/dkuppitz/sql2gremlin


#Extend The Schema
Our objective is to extend the Northwind schema and load some data into the database. We will add an entity describing a Facebook account and relate that to the customer entity.

<p align="left">
  <img src="Northwind-extended.png"/>
</p>

##Schema changes to support the new data
This is for reference only - don't load these statements! - the groovy loader will do it with "create_schema: true".

These are the lines of Gremlin that you could run in the console to extend the schema manually. However we will let DSE Graph loader use our Groovy script to create the schema dynamically when it loads the data.

Reference only!
```
schema.propertyKey("age").Int().single().ifNotExists().create()
schema.propertyKey("confidence").Int().single().ifNotExists().create()
schema.propertyKey("relationshipType").Text().single().ifNotExists().create()
schema.propertyKey("affinityScore").Int().single().ifNotExists().create()
schema.propertyKey("rating").Int().single().ifNotExists().create()

schema.vertexLabel("facebookMember").properties("name", "age").ifNotExists().create()

schema.edgeLabel("isMemberOf").single().properties("confidence").connection("customer", "facebookMember").ifNotExists().create()
schema.edgeLabel("isRelatedTo").single().properties("relationshipType").connection("facebookMember", "facebookMember").ifNotExists().create()
schema.edgeLabel("isFriendsWith").single().properties("affinityScore").connection("facebookMember", "facebookMember").ifNotExists().create()
schema.edgeLabel("rated").single().properties("rating").connection("customer", "product").ifNotExists().create()

schema.vertexLabel('facebookMember').index('byName').materialized().by('name').ifNotExists().add()
```

##Check (or re-create) the csv files
```
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/GeneratedDataAndScripts/GeneratedData
$ ls
facebookMembers.csv  identityEdges_cf2b.csv  isFriendsWith.csv  isRelatedTo.csv  rated.csv
```

##Create the facebook identity and relationship data loader script
```
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ vi supplemental_data_mapping.groovy

config create_schema: true, load_new: false

def inputpath = '/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/GeneratedDataAndScripts/GeneratedData/';

fbMembersInput = File.csv(inputpath + 'facebookMembers.csv').delimiter('|')
identitiesInput = File.csv(inputpath + 'identityEdges_cf2b.csv').delimiter('|')
isFriendsWithInput = File.csv(inputpath + 'isFriendsWith.csv').delimiter('|')
isRelatedToInput = File.csv(inputpath + 'isRelatedTo.csv').delimiter('|')
ratedInput = File.csv(inputpath + 'rated.csv').delimiter('|')

//Specifies what data source to load using which mapper

load(fbMembersInput).asVertices {
    label "facebookMember"
    key "name"
}

load(isFriendsWithInput).asEdges {
    label "isFriendsWith"
    outV "nameFrom", {
        label "facebookMember"
        key "name"
    }
    inV "nameTo", {
        label "facebookMember"
        key "name"
    }
}

load(isRelatedToInput).asEdges {
    label "isRelatedTo"
    outV "nameFrom", {
        label "facebookMember"
        key "name"
    }
    inV "nameTo", {
        label "facebookMember"
        key "name"
    }
}

load(ratedInput).asEdges {
    label "rated"
    outV "customerName", {
        label "customer"
        key "name"
    }
    inV "productId", {
        label "product"
        key "id"
    }

}
```

##Load the Facebook identity and relationship data
```
LOADER_HOME=/opt/dse-graph-loader-5.0.5 export LOADER_HOME

$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ $LOADER_HOME/graphloader ./supplemental_data_mapping.groovy -graph testGRYO -address localhost -dryrun false


2017-01-10 14:15:09 INFO  Reporter:92 - ADD Request for 152 vertices 121 edges 85 properties 0 anonymous
2017-01-10 14:15:09 INFO  Reporter:97 - Current total additions: 237 vertices 121 edges 85 properties 0 anonymous
2017-01-10 14:15:09 INFO  Reporter:99 - 443 total elements written
2017-01-10 14:15:09 INFO  DataLoaderImpl:164 - Scheduling [rated] for reading
2017-01-10 14:15:10 INFO  Reporter:92 - ADD Request for 0 vertices 469 edges 0 properties 0 anonymous
2017-01-10 14:15:10 INFO  Reporter:97 - Current total additions: 237 vertices 590 edges 85 properties 0 anonymous
2017-01-10 14:15:10 INFO  Reporter:99 - 912 total elements written
```

##Create the Customer <-> Facebook edge data loader script
```
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ vi supplemental_fb_edges_mapping.groovy

config create_schema: true, load_new: false

def inputpath = '/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/GeneratedDataAndScripts/GeneratedData/';

def identities = inputpath + 'identityEdges_c2fb.csv';

isMemberOfInput = File.csv(identities).delimiter('|')

load(isMemberOfInput).asEdges {
    label "isMemberOf"
    outV "name", {
        label "customer"
        key "name"
    }
    inV "name", {
        label "facebookMember"
        key "name"
    }
}
```

##Load the Customer <-> Facebook edge data
```
LOADER_HOME=/opt/dse-graph-loader-5.0.5 export LOADER_HOME

$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ $LOADER_HOME/graphloader ./supplemental_fb_edges_mapping.groovy -graph testGRYO -address localhost -dryrun false

2017-01-10 14:25:43 INFO  Reporter:99 - 170 total elements written
2017-01-10 14:25:43 INFO  DataLoaderImpl:347 - Looking for relations in the following loads [identityEdges_c2fb]
2017-01-10 14:25:43 INFO  DataLoaderImpl:193 - Initializing tasks with [1] read threads and [1] loader threads
2017-01-10 14:25:43 INFO  DataLoaderImpl:164 - Scheduling [identityEdges_c2fb] for reading
2017-01-10 14:25:44 INFO  Reporter:92 - ADD Request for 0 vertices 85 edges 0 properties 0 anonymous
2017-01-10 14:25:44 INFO  Reporter:97 - Current total additions: 170 vertices 85 edges 0 properties 0 anonymous
2017-01-10 14:25:44 INFO  Reporter:99 - 255 total elements written
```

#Run Some queries in Studio:

(if you need it)
```
schema.config().option('graph.schema_mode').set('Development')    
```
Everybody in London
```
g.V().hasLabel('customer').has('city','London')
```
Everybody in London with an associated Facebook ID with a confidence level greater than 60%
```
g.V().hasLabel('customer').has('city','London').outE('isMemberOf').has('confidence',gt(60)).inV().valueMap()
```
Everybody in London with an associated Facebook ID with a confidence level greater than 60% and show their combined details:
```
g.V().hasLabel('customer').has('city','London').as("customer").outE('isMemberOf').has('confidence',gt(60)).as("confidence").inV().as("friend")
    .select("customer", "confidence", "friend").by("name").by("confidence").by("name")
```
Another query - ordered group by:
```
gremlin> g.V().hasLabel("product").groupCount().by("unitPrice").order(local).by(keys, decr)

==>{263.5=1, 123.79=1, 97.0=1, 81.0=1, 62.5=1, 55.0=1, 53.0=1, 49.3=1, 46.0=1, 45.6=1, 43.9=2, 40.0=1, 39.0=1, 38.0=2, 36.0=1, 34.8=1, 34.0=1, 33.25=1, 32.8=1, 32.0=1, 31.23=1, 31.0=1, 30.0=1, 28.5=1, 26.0=1, 25.89=1, 25.0=1, 24.0=1, 23.25=1, 22.0=1, 21.5=1, 21.35=1, 21.05=1, 21.0=2, 20.0=1, 19.5=1, 19.45=1, 19.0=2, 18.4=1, 18.0=4, 17.45=1, 17.0=1, 16.25=1, 15.5=1, 15.0=2, 14.0=4, 13.25=1, 13.0=1, 12.75=1, 12.5=2, 12.0=1, 10.0=3, 9.65=1, 9.5=2, 9.2=1, 9.0=1, 7.75=1, 7.45=1, 7.0=1, 6.0=1, 4.5=1, 2.5=1}
```





