# dse-graph-NorthWind-database - work in progress...

1. Setup DSE
------------
upgrade to 5.0.5
download loader and studio (or install dse-demos package)

start Studio 
nohup ./datastax-studio-1.0.2/bin/server.sh &

2. Get Data
-----------
Download the Northwind database data file from https://github.com/dkuppitz/sql2gremlin/blob/master/assets/northwind.kryo

3. Create a mapping file
----------------------

Create northwind-mapping.groovy:
```
//Configures the data loader to create the schema
config create_schema: true, load_new: true

def inputpath = '/Users/jeremy/repos/sql2gremlin/assets/';
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
4. Load The Data
----------------

Delete an old one if there is one:
```
gremlin> :remote config alias g testGRYO.g
gremlin> schema.config().option('graph.schema_mode').set('Development')
gremlin> schema.clear()
```


load data - create graph called testGRYO - change -dryrun to false when ready to load:
```
cd /home/dse/dse_dev/dse-graph-Northwind-loader
./dse-graph-loader-5.0.5/graphloader ./northwind-map.groovy  -graph testGRYO -address localhost -dryrun false
...
2017-01-10 13:36:22 INFO  Reporter:92 - ADD Request for 0 vertices 4077 edges 0 properties 0 anonymous
2017-01-10 13:36:22 INFO  Reporter:97 - Current total additions: 3209 vertices 6177 edges 14554 properties 0 anonymous
2017-01-10 13:36:22 INFO  Reporter:99 - 23940 total elements written
```

5. Create Notebook
------------------

create a new notebook - Northwind, testGRYO database
test its all in there:
```
g.V().count() = 3209
```
sample queries on the data:
http://sql2gremlin.com/
e.g. g.V().hasLabel("category").valueMap("name", "description")

also refer to https://github.com/dkuppitz/sql2gremlin


6. Extend The Schema - don't do this - the groovy loader will do it with "create_schema: true"

https://drive.google.com/drive/folders/0B2W8ihBXvBeJUXJualFYZU1MSDQ

Update the schema:
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

7. Check (or re-create) the csv files
```
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/GeneratedDataAndScripts/GeneratedData
$ ls
facebookMembers.csv  identityEdges_cf2b.csv  isFriendsWith.csv  isRelatedTo.csv  rated.csv
```

8. Create the loader script
```
$ cat supplemental_data_mapping.groovy
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

9.Load the data
```
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ ../../dse-graph-loader-5.0.5/graphloader ./supplemental_data_mapping.groovy -graph testGRYO -address localhost -dryrun false


2017-01-10 14:15:09 INFO  Reporter:92 - ADD Request for 152 vertices 121 edges 85 properties 0 anonymous
2017-01-10 14:15:09 INFO  Reporter:97 - Current total additions: 237 vertices 121 edges 85 properties 0 anonymous
2017-01-10 14:15:09 INFO  Reporter:99 - 443 total elements written
2017-01-10 14:15:09 INFO  DataLoaderImpl:164 - Scheduling [rated] for reading
2017-01-10 14:15:10 INFO  Reporter:92 - ADD Request for 0 vertices 469 edges 0 properties 0 anonymous
2017-01-10 14:15:10 INFO  Reporter:97 - Current total additions: 237 vertices 590 edges 85 properties 0 anonymous
2017-01-10 14:15:10 INFO  Reporter:99 - 912 total elements written
```

10. Load the Custmoer <-> Facebook edges
```
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ ../../dse-graph-loader-5.0.5/graphloader ./supplemental_fb_edges_mapping.groovy -graph testGRYO -address localhost -dryrun false

2017-01-10 14:25:43 INFO  Reporter:99 - 170 total elements written
2017-01-10 14:25:43 INFO  DataLoaderImpl:347 - Looking for relations in the following loads [identityEdges_c2fb]
2017-01-10 14:25:43 INFO  DataLoaderImpl:193 - Initializing tasks with [1] read threads and [1] loader threads
2017-01-10 14:25:43 INFO  DataLoaderImpl:164 - Scheduling [identityEdges_c2fb] for reading
2017-01-10 14:25:44 INFO  Reporter:92 - ADD Request for 0 vertices 85 edges 0 properties 0 anonymous
2017-01-10 14:25:44 INFO  Reporter:97 - Current total additions: 170 vertices 85 edges 0 properties 0 anonymous
2017-01-10 14:25:44 INFO  Reporter:99 - 255 total elements written
```

11. Run Some queries in Studio:

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



























1. Setup DSE
------------
upgrade to 5.0.5
download loader
download studio

start Studio 
./datastax-studio-1.0.2/bin/server.sh

2. Get Data
-----------
download data file https://github.com/dkuppitz/sql2gremlin/blob/master/assets/northwind.kryo

3. Create mapping file
----------------------

Create northwind-mapping.groovy:
===========================================================

//Configures the data loader to create the schema
config create_schema: true, load_new: true

def inputpath = '/Users/jeremy/repos/sql2gremlin/assets/';
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
==========================================================

4. Load The Data
----------------

Delete an old one if there is one:
gremlin> :remote config alias g testGRYO.g
gremlin> schema.config().option('graph.schema_mode').set('Development')
gremlin> schema.clear()



load data - create graph called testGRYO - change -dryrun to false when ready to load:
cd /home/dse/dse_dev/dse-graph-Northwind-loader
./dse-graph-loader-5.0.5/graphloader ./northwind-map.groovy  -graph testGRYO -address localhost -dryrun false
...
2017-01-10 13:36:22 INFO  Reporter:92 - ADD Request for 0 vertices 4077 edges 0 properties 0 anonymous
2017-01-10 13:36:22 INFO  Reporter:97 - Current total additions: 3209 vertices 6177 edges 14554 properties 0 anonymous
2017-01-10 13:36:22 INFO  Reporter:99 - 23940 total elements written


5. Create Notebook
------------------

create a new notebook - Northwind, testGRYO database
test its all in there:
g.V().count() = 3209

sample queries on the data:
http://sql2gremlin.com/
e.g. g.V().hasLabel("category").valueMap("name", "description")

also refer to https://github.com/dkuppitz/sql2gremlin


6. Extend The Schema - don't do this - the groovy loader will do it with "create_schema: true"

https://drive.google.com/drive/folders/0B2W8ihBXvBeJUXJualFYZU1MSDQ

Update the schema:
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


7. Check (or re-create) the csv files
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/GeneratedDataAndScripts/GeneratedData
$ ls
facebookMembers.csv  identityEdges_cf2b.csv  isFriendsWith.csv  isRelatedTo.csv  rated.csv


8. Create the loader script

$ cat supplemental_data_mapping.groovy
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


9.Load the data
 
$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ ../../dse-graph-loader-5.0.5/graphloader ./supplemental_data_mapping.groovy -graph testGRYO -address localhost -dryrun false


2017-01-10 14:15:09 INFO  Reporter:92 - ADD Request for 152 vertices 121 edges 85 properties 0 anonymous
2017-01-10 14:15:09 INFO  Reporter:97 - Current total additions: 237 vertices 121 edges 85 properties 0 anonymous
2017-01-10 14:15:09 INFO  Reporter:99 - 443 total elements written
2017-01-10 14:15:09 INFO  DataLoaderImpl:164 - Scheduling [rated] for reading
2017-01-10 14:15:10 INFO  Reporter:92 - ADD Request for 0 vertices 469 edges 0 properties 0 anonymous
2017-01-10 14:15:10 INFO  Reporter:97 - Current total additions: 237 vertices 590 edges 85 properties 0 anonymous
2017-01-10 14:15:10 INFO  Reporter:99 - 912 total elements written


10. Load the Custmoer <-> Facebook edges

$ pwd
/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/LoaderScripts

$ ../../dse-graph-loader-5.0.5/graphloader ./supplemental_fb_edges_mapping.groovy -graph testGRYO -address localhost -dryrun false

2017-01-10 14:25:43 INFO  Reporter:99 - 170 total elements written
2017-01-10 14:25:43 INFO  DataLoaderImpl:347 - Looking for relations in the following loads [identityEdges_c2fb]
2017-01-10 14:25:43 INFO  DataLoaderImpl:193 - Initializing tasks with [1] read threads and [1] loader threads
2017-01-10 14:25:43 INFO  DataLoaderImpl:164 - Scheduling [identityEdges_c2fb] for reading
2017-01-10 14:25:44 INFO  Reporter:92 - ADD Request for 0 vertices 85 edges 0 properties 0 anonymous
2017-01-10 14:25:44 INFO  Reporter:97 - Current total additions: 170 vertices 85 edges 0 properties 0 anonymous
2017-01-10 14:25:44 INFO  Reporter:99 - 255 total elements written


11. Run Some queries in Studio:

(if you need it)
schema.config().option('graph.schema_mode').set('Development')    

Everybody in London
g.V().hasLabel('customer').has('city','London')

Everybody in London with an associated Facebook ID with a confidence level greater than 60%
g.V().hasLabel('customer').has('city','London').outE('isMemberOf').has('confidence',gt(60)).inV().valueMap()

Everybody in London with an associated Facebook ID with a confidence level greater than 60% and show their combined details:
g.V().hasLabel('customer').has('city','London').as("customer").outE('isMemberOf').has('confidence',gt(60)).as("confidence").inV().as("friend")
    .select("customer", "confidence", "friend").by("name").by("confidence").by("name")






















