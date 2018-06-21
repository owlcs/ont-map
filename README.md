# An OWL2 ontology to ontology data mapper (builder and inference engine).

### New version of https://git.avicomp.ru/ontology-editor/ont-map
### Based on [ONT-API, ver 1.2.1](https://github.com/avicomp/ont-api)

### Notes, propositions and examples
* _ru.avicomp.map.Managers_ is the main class to access to the system 
* API should only provide access to the spin functions which acceptable with OWL2 model in mapping terms, all other should be hidden. 
Examples of reasons of exclusion/hide:
    - `smf:lastModified` - function to get the file timestamp. It is excluded since API should not work with the file system. 
    - `sp:notExists` - function to use as part of SPARQL query (filter). API should not allow explicit queries as functional parameters.  
    - `spinmap:targetResource` - is used implicitly to bind two contexts, in order to control behaviour it is better to prohibit any other usage. 
    - `spl:primaryKeyURIStart` - a part of a complex and turbid SPIN-API mechanism, right now I do not see any possibility to use this functionality.
    - etc.
* All functions are supplemented with complete information about arguments and types to be used as elements of constructor in gui, any inappropriate usage (e.g. incompatible types) should cause an error.
* API can work only with OWL2 entities: the context arrow connects two OWL class expressions [OntCE](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntCE.java), 
to make contexts references OWL object property ([OntOPE](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntOPE.java)) is used, 
and to map data (make a property bridge in Diagram) OWL annotation ([OntNAP](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntNAP.java)) and OWL datatype ([OntNDP](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntNDP.java)) properties are used.
* Mapping inference should produce OWL named individuals [OntIndividual.Named](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntIndividual.java). 
Anonymous individuals are theoretically possible, but currently are not supported due to SPIN-API limitations related to target functions.
* Although it is not a direct part of the API, there is also a ru.avicomp.map.ClassPropertyMap to draw class-boxes with all related properties.
* A simple mapping example with inference:

        // get manager:
        MapManager manager = Managers.getMapManager();
        // get built-in prefixes:
        PrefixMapping pm = manager.prefixes();
        // get function: 
        MapFunction changeNamespace = manager.getFunction(pm.expandPrefix("spinmapl:changeNamespace"));
        // get source class from some ontology:
        OntCE source = ...
        // get target class from some ontology:
        OntCE target = ...
        // build target function-call:
        String arg = pm.expandPrefix("spinmapl:targetNamespace");
        MapFunction call = changeNamespace.create().add(arg, "http://example.com#").build();
        // create mappin-model:
        MapModel mapping = manager.createMapModel();
        // build simple map-context:
        mapping.createContext(source, target, call);
        // run spin-inference:
        manager.getInferenceEngine().run(mapping, ..., ...);
        
* Printing all supported functions:

        MapManager manager = Managers.getMapManager();
        manager.functions()
                .sorted(Comparator.comparing((MapFunction f) -> !f.isTarget()).thenComparing(MapFunction::returnType).thenComparing(MapFunction::name))
                .forEach(System.out::println);