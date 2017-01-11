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
