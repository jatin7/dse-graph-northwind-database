config create_schema: true, load_new: false

def inputpath = '/home/dse/dse_dev/dse-graph-Northwind-loader/extend_schema/GeneratedDataAndScripts/GeneratedData/';

fbMembersInput = File.csv(inputpath + 'facebookMembers.csv').delimiter('|')
identitiesInput = File.csv(inputpath + 'identityEdges_c2fb.csv').delimiter('|')
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
