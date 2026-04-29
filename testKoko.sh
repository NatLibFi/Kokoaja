#! /bin/bash

set -e

~/fuseki/fuseki restart
cd ~/git/Finto-data
git pull
cd ~/git/Kokoaja
./compile.sh
./run_local.sh
cp koko-test.ttl ~/git/Finto-data/vocabularies/koko/.
cd ~/git/Finto-data/vocabularies/koko
./toskos_test.sh

DATASET='http://localhost:3030/test'
graph='http://www.yso.fi/onto/koko-testi/'
type='text/turtle'

curl -sT ~/git/Finto-data/vocabularies/koko/koko-skos_test.ttl -H "Content-Type: $type" "$DATASET?graph=$graph"

echo "Muutokset viety paikalliselle Fusekille Skosmoksessa tarkastettavaksi. Julkaisun yhteydessä muista ajaa myös Kokoajalle push-changes-to-git.sh"
