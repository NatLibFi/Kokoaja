#!/bin/bash

VASTAAVUUDET=kokoUriVastaavuudet.txt
VASTAAVUUDETUUDET=kokoUriVastaavuudet-test.txt
YSO=../../vocabularies/yso/yso-skos.ttl
KOOSTUMUS=conf/kokoKoostumus.txt
KOKOOLD=../../vocabularies/koko/koko-skos.ttl
KOKONEW=../../vocabularies/koko/koko-test.ttl
MUSTALISTA=conf/kokoMustalista.txt

ARGS="$VASTAAVUUDET $YSO $KOOSTUMUS $VASTAAVUUDETUUDET $KOKOOLD $KOKONEW $MUSTALISTA"

java -Xmx4G -cp 'lib/*:.' Kokoaja2 $ARGS 2>&1 | tee kokoaja.log

