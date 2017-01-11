import com.datastax.dsegraphloader.api.File

//Configures the data loader to create the schema
config create_schema: true, load_new: true

hospital = File.text("hospitals.csv").delimiter(",")
patient = File.text("patient.csv").delimiter(",")
the_edges = File.text("hospital.csv").delimiter(",")

load(patient).asVertices {
    label "Patient"
    key "patient"
}

load(hospital).asVertices {
    label "Hospital"
    key "hospital"
}

//take the flat data and transform it into a hierarchy so that
// the edges can be created from a single input record
the_edges = the_edges.transform {
    it['patient'] = [
            'id1' : it['patient_id_1'],
            'id2' : it['patient_id_2'] ];
    it['hospital'] = [
            'id1' : it['hospital_id_1'],
            'id2' : it['hospital_id_2']
    ];
    it
}

load(the_edges).asEdges  {
    label "visit"
    outV "patient", {
        label "Patient"
        key id1:"id1", id2:"id2"
    }
    inV "hospital", {
        label "Hospital"
        key id1:"id1", id2:"id2"
    }
}





