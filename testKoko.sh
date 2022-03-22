cd ~/git/Finto-data/tools/kokoaja
cp ~/eclipse-workspace/Kokoaja/src/*.java .
./compile.sh
./run_local.sh
cd ~/vocabularies/koko
./toskos_test.sh
#curl -X POST http://localhost:3030/testi?update=DROP%20ALL
#~/jena/jena-fuseki2/apache-jena-fuseki/bin/s-put http://localhost:3030/testi http://www.yso.fi/onto/koko-testi/ ~/vocabularies/koko/koko-skos_test.ttl
