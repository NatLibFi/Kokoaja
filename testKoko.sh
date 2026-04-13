#! /bin/bash

~/fuseki/fuseki restart
cd ~/git/Finto-data
git pull
cd ~/git/Kokoaja
./compile.sh
./run_local.sh
cd ~/git/Finto-data/vocabularies/koko
./toskos_test.sh

DATASET='http://localhost:3030/test'
graph='http://www.yso.fi/onto/koko-testi/'
type='text/turtle'

curl -sT ~/git/Finto-data/vocabularies/koko/koko-skos_test.ttl -H "Content-Type: $type" "$DATASET?graph=$graph"
