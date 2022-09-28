cd ~/git/Kokoaja
cp ~/eclipse-workspace/Kokoaja/src/*.java .
./compile.sh
./run_local.sh
cd ~/git/Finto-data/vocabularies/koko
./toskos_test.sh
curl -X POST http://localhost:3030/test?update=DROP%20ALL
/opt/fuseki/bin/s-put http://localhost:3030/test http://www.yso.fi/onto/koko-testi/ ~/git/Finto-data/vocabularies/koko/koko-skos_test.ttl
