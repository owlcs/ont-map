# ONT-MAP - an OWL2 ontology to ontology data mapper (builder and inferencing engine).

## Summary
**ONT-MAP** provides a convenient way to build a mapping between two [OWL2](https://www.w3.org/TR/owl2-overview/) ontologies 
and also an inferencing-engine to transfer and transform the ontological data in accordance with that mapping. 
The mapping can be serialized as a [SPIN](http://spinrdf.org/)-rules RDF file, and, therefore, is Topbraid-compatible.

## Motivation
Pure SPIN, which is an RDF-superstructure over SPARQL, is a fairly complex language when it comes to mapping between ontologies, 
and it is incompatible with OWL2.
**ONT-MAP** offers a way for building mappings without any knowledge about SPIN or SPARQL, 
just only through top-level operations and objects, which can be represented as some pseudo-language.
The interfaces and implementations are separated, 
and it is planned in the future to switch from SPIN to some another inferencing language, such as SHACL.

## Dependencies 
 - **[ONT-API, ver 1.4.0-SNAPSHOT](https://github.com/avicomp/ont-api)** the OWL-API implementation on top of Jena
 - **[Topbraid SHACL, ver 1.0.1](https://github.com/TopQuadrant/shacl)** the last public version that supports SPIN
 - [Jena-ARQ, ver 3.8.0](https://github.com/apache/jena) transitively from ONT-API
 - [OWL-API, ver 5.1.7](https://github.com/owlcs/owlapi) transitively from ONT-API
 
## License
* Apache License Version 2.0

### Notes, propositions and examples
* _ru.avicomp.map.Managers_ is the main class to access to the system.
* API provides access only to those spin functions which acceptable with OWL2 model in mapping terms, all other are hidden or rejected. 
Examples of reasons of exclusion/hide:
    - `smf:lastModified` - function to get the file timestamp. It is excluded since API should not work with the file system or other resources. 
    - `sp:notExists` - function to use as part of SPARQL query (filter). API should not allow explicit queries as functional parameters.  
    - `spinmap:targetResource` - is used implicitly to bind two contexts, in order to control behaviour it is better to prohibit any other usage. 
    - `spl:primaryKeyURIStart` - a part of a complex and turbid SPIN-API mechanism, right now I do not see any possibility to use this functionality.
    - etc.
* In addition to the standard spin functions (from spif, spinmap, fn and other spin vocabularies) 
there are also ONT-MAP functions (such as `avc:UUID`, `avc:currentIndividual`, etc), math functions (`math:log10`, `math:atan2`) and some other. 
So, a set of functions that can be used in mappings, has been expanded from one side, and narrowed with another.  
* All functions are supplemented with complete information about arguments and types to be used as elements of constructor in GUI, any inappropriate usage (e.g. incompatible types) causes an error.
* API can work only with OWL2 entities: the context arrow connects two OWL class expressions [OntCE](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntCE.java), 
to make contexts references OWL object property ([OntOPE](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntOPE.java)) is used, 
and to map data (make a property bridge in Diagram) OWL annotation ([OntNAP](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntNAP.java)) and OWL datatype ([OntNDP](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntNDP.java)) properties are used.
* The mapping inferencing creates OWL Named Individuals [OntIndividual.Named](https://github.com/avicomp/ont-api/blob/master/src/main/java/ru/avicomp/ontapi/jena/model/OntIndividual.java). 
Anonymous individuals are theoretically possible, 
but currently are not supported due to SPIN-API limitations related to target functions.
* There is also a `ru.avicomp.map.ClassPropertyMap`, that is responsible to provide class-properties hierarchical relations, which can be used to draw class-boxes with all related properties.
* A simple mapping example with inferencing:

        // get the manager:
        MapManager manager = Managers.getMapManager();
        // get built-in prefixes:
        PrefixMapping pm = manager.prefixes();
        // get some function: 
        MapFunction changeNamespace = manager.getFunction(pm.expandPrefix("spinmapl:changeNamespace"));
        // get the source class from some ontology:
        OntCE source = ...
        // get the target class from some ontology:
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
                
### TODO:
* more aggregate functions (currently there is only single)
* spin support for all fn-functions
* mechanism to save in a manager a function-call chain as a custom function       
* bugfix and more tests 
* etc         