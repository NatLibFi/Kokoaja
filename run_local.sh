#!/bin/bash

VASTAAVUUDET=kokoUriVastaavuudet.txt
VASTAAVUUDETUUDET=kokoUriVastaavuudet-test.txt
YSO=../Finto-data/vocabularies/yso/yso-vb-skos.ttl
KOOSTUMUS=conf/kokoKoostumus.txt
KOKOOLD=../Finto-data/vocabularies/koko/koko-skos_edellinen.ttl
KOKONEW=../Finto-data/vocabularies/koko/koko-test.ttl
MUSTALISTA=conf/kokoMustalista.txt
REPLACEDBY=../Finto-data/vocabularies/koko/koko-replacedby.ttl

ARGS="$VASTAAVUUDET $YSO $KOOSTUMUS $VASTAAVUUDETUUDET $KOKOOLD $KOKONEW $MUSTALISTA $REPLACEDBY"

java -Xmx4G -cp 'lib/*:.' Kokoaja2 $ARGS 2>&1 | tee kokoaja.log

