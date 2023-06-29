#!/bin/bash

VASTAAVUUDET=kokoUriVastaavuudet.txt
YSO=../../vocabularies/yso/yso-skos.ttl
KOOSTUMUS=conf/kokoKoostumus.txt
KOKOOLD=../../vocabularies/koko/koko-skos.ttl
KOKONEW=../../vocabularies/koko/koko.ttl
MUSTALISTA=conf/kokoMustalista.txt
REPLACEDBY=../../vocabularies/koko/koko-replacedby.ttl

ARGS="$VASTAAVUUDET $YSO $KOOSTUMUS $VASTAAVUUDET $KOKOOLD $KOKONEW $MUSTALISTA $REPLACEDBY"

java -Xmx4G -cp 'lib/*:.' Kokoaja2 $ARGS 2>&1 | tee kokoaja.log

