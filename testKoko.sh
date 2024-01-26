service fuseki start
cd ~/git/Finto-data
git pull
cd ~/git/Kokoaja
cp ~/eclipse-workspace/Kokoaja/src/*.java .
./compile.sh
./run_local.sh
cd ~/git/Finto-data/vocabularies/koko
./toskos_test.sh
/opt/fuseki/bin/s-put http://localhost:3030/test http://www.yso.fi/onto/koko-testi/ ~/git/Finto-data/vocabularies/koko/koko-skos_test.ttl
