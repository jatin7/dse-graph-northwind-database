# DSE Graph Loader

DSE Graph Loader is a customizable, highly tunable command line utility for loading small to medium size graph datasets
into the DSE Graph database from various input sources.

## Usage

DSE Graph Loader is invoked on the command line with a loading script as argument and a variable number of configuration
option-value pairs.

```bash
java -jar dse-graph-loader.jar loadingScript [[-option value]...]
```

The loading script specifies what input data is being loaded and how that data maps onto the graph. For example, the
following invocation loads vertex data from a CSV file into the configured graph.

```bash
java -jar dse-graph-loader.jar ./scripts/csv2Vertex.groovy -filename User.csv -graph csvTest -address 127.0.0.1
```

We could have appended `-label User` to specify the label of the vertex which is populated by this load (by default, the
above script uses the filename without extension which has the same result in this case). To see a list of all available
configuration options for a particular loading script, run

```bash
java -jar dse-graph-loader.jar ./scripts/csv2Vertex.groovy -help
```

which produces the output shown below that contains all of the available configuration options. Those marked with `*` at
the beginning of the line are required and must be specified when executing a loading process.

```text
Usage: graphloader mappingFileName [[-option value]...]
            (to load data into a graph according to the mapping)
       graphloader mappingFileName -help
            (to show this output and all available configuration options)
where options include (* marks required options, others are optional):
	-timeout                 Number of milliseconds until a connection times out. Data type: Integer. Default: [120000]
*	-graph                   The name of the graph to load into. Data type: String.
*	-address                 The ip address (and port) of the DSE Graph instance to connect to. Data type: String.
	-username                The username used to authenticate with DSE. Data type: String. Default: []
	-password                The password used to authenticate with DSE. Data type: String. Default: []
	-read_threads            No of threads to use for reading data from data input. Data type: Integer. Default: [1]
	-load_edge_threads       No of threads to use for loading edge and property data into the graph (0 will force the value to be the number of nodes in the DC * 6). Default: [1]
    -load_vertex_threads     No of threads to use for loading vertices into the graph (0 will force the value to the number of cores/2). Default: [1]
	-dryrun                  Whether to only conduct a trial run to verify data integrity and schema consistency. Data type: Boolean. Default: [false]
	-preparation             Whether to do a preparation run to analyze the data and update the schema (if necessary). Data type: Boolean. Default: [true]
	-preparation_limit       The number of records that the preparation phase will use to attempt to determine if the schema should be updated. Zero indicates no limit. Default: [0]
	-create_schema           Whether to update or create the schema for missing schema elements. Data type: Boolean. Default: [false]
	-schema_output           The name of the file to save the proposed schema in when executing a dry-run. Leave blank to disable. Data type: String. Default: [proposed_schema.txt]
	-vertex_complete         The loader assumes that all vertexes referenced by properties and edges in this load are also included as vertexes of this load. No new vertices will be created from edge data or property data files Data type: Boolean. Default: [false]
	-batch_size              Size of loading batches. Data type: Integer. Default: [100]
	-queue_size              Data retrieval queue size. Data type: Integer. Default: [10000]
	-load_new                Whether the vertices loaded are new and do not yet exist in the graph. Data type: Boolean. Default: [false]
	-driver_retry_attempts   number of retry attempts, if greater than 0, requests will be re-submitted after some recoverable failures. Data type: Integer. Default: [3]
	-driver_retry_delay      ms between driver retries. Data type: Integer. Default: [1000]
	-abort_on_num_failures   Number of failures after which loading is aborted, zero means unlimited. Data type: Integer. Default: [100]
	-reporting_interval      Number of seconds between each progress report written to the log. Data type: Integer. Default: [1]
	-abort_on_prep_errors    Normally if errors occur in the preparation, or during the vertex insertion phase we abort, setting this to false will force the loader to continue up to the maximum number of allowed failures. Data type: Boolean. Default: [true]
	-load_failure_log        The name and location of the file where failed records will be stored. Data type: String. Default: [load_failures.txt]
```

The DSE Graph Loader is built for data loads up to 100s million vertices and billions of edges assuming:

* The graph loader utility is run on a sufficiently powerful machine with enough memory to cache all vertices and enough
cores to parallelize the loading process. When loading the vertexes are stored in a persistent cache. The serialized vertexes may be 
substantially larger (some times 10x) the size of the original data. If not enough shared memory (mmap, or buffer cache)
is available then loading properties and edges will be bound by the speed of the available IO. The graph loader should not
be run on the same machine that runs DSE Graph for larger scale loads. It should be ensured that no other local process is
interfering with the graph loader and that the network connection from the machine to the DSE Graph cluster has enough bandwidth.

* The graph loader is started with enough heap space, e.g. `java -Xmx2g -jar dse-graph-loader.jar`

DSE Graph Loader logs a lot of useful information during the loading process outlining exactly what is happening behind the
scenes and providing detailed information on errors. The loading process goes through 3 stages:

* *Preparation:* Reads over the entire input data to ensure that the data conforms to the graph schema or to update the
graph schema according to the provided data (if enabled). At the end of this stage, statistics are provided on how much
data will be added to the graph (those statistics are estimates) but no data has been loaded yet. Set `-dryrun true` to
abort the loading process after the preparation stage and before any changes are made in the graph itself to inspect the
output and verify that it matches your expectations - this is particularly useful before the first run to spot mistakes.

**create_schema** will not detect values that use multiple cardinality. Those elements must be created manually.

* *Vertex Loading:* The second stage adds or retrieves all of the vertices in the input data and caches them locally to
speed up the subsequent edge loading.

* *Edge and Property Loading:* Adds all edges and properties form the input data to the graph.

## Custom Loading Scripts

The `scripts/` directory contains a number of predefined loading scripts for use.
However, most real world data loading use cases are too complex to be accommodated by a predefined script and require
the development of a custom loading script.

A custom loading script defines two things: *data inputs* and *data mappings*. The integration tests for DSE Graph Loader
in the package `com.datastax.dsegraphloader.integration` provide a number of custom loading script examples that can
serve as a starting point or inspiration.

### Data Input

A data input defines where the data is coming from, how to read/parse the data, how to normalize the data, and any custom
 data transformations the user wishes to execute on the data prior to loading.
All input data is normalized into a *document structured* representation, i.e. a nested maps data structure with `String` keys.

#### File Input

DSE Graph Loader supports the following file based data inputs, all of which are accessed via `File.`:

* CSV: `input = File.csv(filename).delimiter('|')`
* JSON: `input = File.json(filename)`
* Delimited Text: `input = File.text(filename).delimiter("::").header('userid', 'gender', 'age', 'zip')`
* Text parsed by regular expressions: `input = File.text(filename).regex("id:([A-Z0-9-]+)\\sip:([0-9.]+):country=([a-zA-Z]+)").header('id', 'ip', 'country')`

All file based input formats support compression by appending `.gzip()` or `.xzip()`.

#### Null or Empty field handling in File and CSV based inputs

Fields that contain "null", "NULL" or empty fields such as: `field1,,field3` will be pruned in the loader. If you
wish to set a default value for those fields then a transformation can be created on the source data. Examples of this
type of transformation can be found in the scripts directory.

The JSON data input naturally maps the input data onto a document structured representation. The CSV and text based
data inputs read rows of input data that is transformed into a flat document structure where the column headers are the
document fields and the column values the associated value. The header can be overwritten for those data inputs.

#### Distributed Filesystem Support

Some support for distributed filesystems exists.  Specifically this has been tested for CSV, JSON, and Delimited Text (as
described under _File Input_ above).

* HDFS: support is built off of the apache-hadoop-client 2.6.0. HDFS input files are specified by using the hdfs:// scheme. Local configuration should be picked up, this should work like any other HDFS client application.
* S3: Support is based on the official AWS client libraries for S3. Use a scheme that follows s3://[bucket]/[object]. For testing we added the ability to use custom endpoints, a custom endpoint can be specified through the environment variable "s3.loader.endpoint".


#### Database Input

DSE Graph Loader supports reading input data directly from a JDBC compatible database:

```groovy
db = Database.connection("jdbc:h2:./src/test/resources/movie/test").H2().user("sa")
input = db.query "select * from PUBLIC.USER";
```

First, we establish a database connection `db` from which we can generate many data inputs through SQL queries. We call `db`
a *data source* since it gives rise to multiple data inputs.


##### Transformations

All data inputs support arbitrary user transformations to restructure, manipulate, or truncate the input data according
to a user provided function. Recall that the canonical data record representation is a document structure or nested map.
The transformation therefore acts upon such a nested map and must return a nested map. It needs to be ensured that any
provided transformation function is thread-safe or the behavior of the data loader becomes undefined.

```groovy
input = File.text(filename).delimiter("::").header('userid', 'gender', 'age', 'zip')
input = input.transform { it['gender'] = it['gender'].toLowerCase(); it }
```
The example above defines a text delimited data input with a custom header. The transformation function ensure that `gender`
column has only lowercase values.


### Data Mapping

A data mapping defines how the (document structured) input records for a particular data input are mapped into the graph.
First, you have to decide: Does the data input contain vertex or edge records?
If the data input defines vertex records, the data mapping is defined via
```groovy
load(source1).asVertices(vertexMapper)
```
and if you are loading edge data use
```groovy
load(source1).asEdges(edgeMapper)
```

`vertexMapper` and `edgeMapper` define the actual mapping and trace the input record documents by mapping their fields
 onto graph elements. Let's take a look at two examples to explain how such a mapping is defined.

#### GitHub Events

In this first example, we want to create a graph out of the GitHub events that GitHub posts on its [archive](https://www.githubarchive.org/).
In its own words, GitHub Archive is a project to record the public GitHub timeline, archive it, and make it easily
accessible for further analysis. We want to do such analysis and for that we are creating a graph of the repositories, users,
and various events surrounding those.

GitHub Archive makes the event data available as JSON records that look like this:

```json
{
   "id":"3442726273",
   "type":"PushEvent",
   "actor":{
      "id":973692,
      "login":"francois-b",
      "gravatar_id":"",
      "url":"https://api.github.com/users/francois-b",
      "avatar_url":"https://avatars.githubusercontent.com/u/973692?"
   },
   "repo":{
      "id":48007081,
      "name":"francois-b/DeepLearningMovies",
      "url":"https://api.github.com/repos/francois-b/DeepLearningMovies"
   },
   "payload":{
      "push_id":903016347,
      "size":1,
      "distinct_size":1,
      "ref":"refs/heads/master",
      "head":"ba2c0fb41f23daf7e60f31676f44541664a62bf4",
      "before":"42cf599fd5281b534c92e005a1b0b358b8869d8f",
      "commits":[
         {
            "sha":"ba2c0fb41f23daf7e60f31676f44541664a62bf4",
            "author":{
               "email":"f.bouet@gmail.com",
               "name":"Francois"
            },
            "message":"Further PEP8 clean up",
            "distinct":true,
            "url":"https://api.github.com/repos/francois-b/DeepLearningMovies/commits/ba2c0fb41f23daf7e60f31676f44541664a62bf4"
         }
      ]
   },
   "public":true,
   "created_at":"2015-12-15T00:00:00Z"
}
```

Hence, we write the following loading script.

```groovy
//Defines the data input (a file which is specified via command line arguments)
input = File.json(inputfilename)

//Defines the mapping from input record to graph elements
eventMapper = {
    key "id"                        // The "id" field is the key or unique identifier for this event vertex
    labelField "type"               // The label for the event vertex is extracted from the "type" field
    outV "actor", "hasActor", {     // The "actor" field links this event to vertex in the out-direction via a "hasActor" edge...
        label "User"                // ... and that vertex has the label "User"
        key "id"                    // ... and is also unique identified by its "id"
    }
    outV "repo", "hasRepository", {  // The "repo" field links this event to a vertex in the out-direction via a "hasRepository" edge...
        label "Repository"          // ... and that vertex has the label "Repository"
        key "id"                    // ... and is also unique identified by its "id"
    }
    ignore "payload"                //ignore the "payload" field and anything beneath
    ignore "public"                 //ignore the "public" field
    ignore "org"                    //ignore the "org" field (which is only present on some events)
                                    //All other fields that are not explicitly mentioned in this mapping are mapped on vertex properties
}

//Load vertex records from the input according to the mapping
load(input).asVertices(eventMapper)
```

First, we define the data input as a file containing json records. `filename`
is a variable in the script which gets populated by configuration options from the command line (i.e. `-filename`) and
allows us to reuse this script for any of the GitHub Archive files we downloaded.

Next, we define the `eventMapper` which traces the input record documents and declares how the individual fields map
onto graph elements. Finally, we put the two together via the `load` command.

Note, that the "key" declarations are of particular importance because they define how a vertex is uniquely identified
so that we avoid creating duplicate data in the graph. The keyed property must either be indexed or the vertex's custom id
in order to ensure acceptable performance when looking for existing vertices.

#### Movie Ratings

In this example, we create a graph of users, movies and user-movie ratings. Such a graph could be used to build a movie
recommendation engine. We assume that the data is read out of a JDBC compliant database ([H2](http://www.h2database.com/)
 in this example).

```groovy
//Defines the data source as a database connection
db = Database.connection("jdbc:h2:" + database).H2().user("sa")

//Define multiple data inputs from the database source via SQL queries
userInput = db.query "select * from PUBLIC.USER"
movieInput = db.query "select * from PUBLIC.MOVIE"
ratingInput = db.query "select * from PUBLIC.RATINGS"

//Specifies how to map the user data onto vertices
load(userInput).asVertices {
    label "User"    // the input record is mapped onto a vertex with label "User"
    key "userid"    // ... which is uniquely identified by the key "userid"
                    // All other fields/columns map onto vertex properties
}

//Specifies how to map the movie data onto vertices
load(movieInput).asVertices {
    label "Movie"   // the input record is mapped onto a vertex with label "Movie"
    key "movieid"   // ... which is uniquely identified by the key "movieid"
                    // All other fields/columns map onto vertex properties
}

//Specifies how to map the ratings data onto edges
load(ratingInput).asEdges {
    label "rates"       // the input record is mapped onto an edge with label "rates"
    outV "userid", {    // the out-vertex of that edge is defined by the field "userid"
        label "User"    // ... which maps onto a "User" vertex
        key "userid"    // ... and the value provided in the field maps onto the "userid" key of that vertex
    }
    inV "movieid", {    // the in-vertex of that edge is defined by the field "movieid"
        label "Movie"   // ... which maps onto a "Movie" vertex
        key "movieid"   // ... and the value provided in the field maps onto the "movieid" key of that vertex
    }
                        // All other fields/columns map onto edge properties
}

config load_new: true   // We guarantee that all vertex records are new to improve loading performance
```

In this loading script we first define a database connection as a data source from which we can generate multiple
data inputs.

For each data input we define a mapping and associate it with the input through the `load` command. Unlike the example above,
the mappings are defined inline.

Note the configuration `load_new: true` in the script above. This declares that the vertex records do not yet exist in the graph at
the beginning of the loading process. Configuring `load_new` can significantly speed up the loading process.
However, the user must guarantee that the vertex records are indeed new - otherwise there will be duplicate vertices in the graph.

## Additional Information and FAQ

* For more documentation, please refer to the JavaDoc of the `com.datastax.dsegraphloader.api` package.



## Contributing a Custom Loading Script

The `scripts/` directory contains a number of loading scripts for common graph loading use cases.  Open a pull request
against the repository to contribute such a script and note the following:

* The script should be written in such a way that it is _general purpose_, i.e. fulfills more than just a very specific
loading need. That often means that key aspects of the script can be controlled by unbound variables which are defined
through command line configuration options so the script can be tailored to a specific use case upon execution and
without having to modify the script.

* The script must be accompanied by a corresponding `*.config` configuration file which enumerates all of the unbound
 variables in the loading script with an intelligible description and reasonable defaults (if possible).

* The script should have an associated integration test in `com.datastax.dsegraphloader.integration.scripts`. Copy an
existing integration test in this package as a starting point.

## Development of DSE Graph Loader

Building this project creates an executable jar that can be invoked as described above.

### Running DSE Graph Integration Tests

Running the integration tests of DSE Graph Loader requires that CCM is installed and the tests are invoked with special
parameters. Specifically:

* Install CCM https://github.com/pcmanus/ccm#installation

* Make sure you install version 2.1.7 or later

* Configure your loopback address: `sudo ifconfig lo0 alias 127.0.1.1 up`

* Set VM options to `-ea -Ddse=true -Dcassandra.directory=[DSE DIRECTORY] -Dcassandra.version=5.0`
where `[DSE DIRECTORY]` points to the local build of DSE, e.g. `/Users/bcoverston/github/bdp`,or run tests via:
`mvn surefire:test -Dtest=GithubIntegrationTestDse -Dcassandra.directory=[DSE DIRECTORY]`

In addition a specific profile called `dse` is present in the default build file for this project. In addition to the
normal integration tests, it will also execute all of the DSE specific tests. One way to execute this profile is to run
`mvn integration-test --activate-profiles dse`. Using this profile on the install target will also run the DSE specific tests. The only
prerequisite to running these tests is to have the environment variable `DSE_HOME` set to the local DSE directory. Alternatively
if the DSE specific version of [ccm](https://github.com/riptano/ccm) is installed it should pull down the required binaries.

### Integration Tests using TinkerPop

The integration tests against DSE Graph take a long time to start up. To test various aspects of the graph loader
(other than the integration with DSE Graph and the DSE Graph driver) it is therefore recommended to build integration
tests against TinkerGraph.
Consult `AbstractTinkerGraphIntegrationTest` and its sub-classes for examples on how to do that.

### Loading Data using Neo4J as a source

The data loader supports using Neo4J as a data source through Gremlin. To support this the data loader uses not only
`neo4j-gremlin`, but also `neo4j-tinkerpop-api-impl`. While both of these projects are Apache 2 licensed,
`neo4j-tinkerpop-api-impl` depends on AGPL licensed libraries which we cannot distribute. For convenience we have
provided a script `get-neo4j-deps` which can be run prior to running the `graphloader` shell script. If run from the
same directory as the `graphloader`, `get-neo4j-deps` will create a `lib` directory that includes the required
dependencies. The `lib` directory will be detected and added to the `-cp` argument when the java command is run from
`graphloader`. If for some reason you are using the `java -jar` command directly to invoke the graph loader add the lib
directory to the classpath to add the necessary dependencies.
