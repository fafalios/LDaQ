# LDaQ - Finding and Transforming Linked Data-answerable SPARQL queries

This library provides the following algorithms:
- IsLinkedDataAnswerableBGP: Algorithm for checking if a Basic Graph Pattern (BGP) is Linked Data-answerable, i.e., it can be evaluated through link traversal, without accessing an endpoint.
- IsLinkedDataAnswerableQuery: Algorithm for checking if a SPARQL query is Linked Data-answerable 
- transformBGP: Algorithm for transforming a Linked Data-answerable BGP a SPARQL-LD query
- transformQuery: Algorithm for transforming a LDaQ to a SPARQL-LD query
- getPattern: Method for getting the pattern of a SPARQL query (variables are replaced by [V], URIs by [U], literals by [L], etc.)

The file "Patterns of LDaQ and non-LDaQ.zip" contains the results of a pattern-based analysis of a large dataset of SPARQL queries (more in README.txt which is inside the zip file).
