#!/bin/bash

ontos=( yso afo juho jupo kauno kito kto kulo maotao mero muso soto tero tsr valo )

put=~/jena/jena-fuseki2/apache-jena-fuseki/bin/s-put
dataset=ontopilvi
address=http://localhost:3030/$dataset

curl 'http://localhost:3030/$/datasets' -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8' --data "dbName=$dataset&dbType=mem" &>/dev/null

curl -X POST $address?update=DROP%20ALL

for i in "${ontos[@]}"
do
  echo "Ladataan $i..."
  $put $address http://www.yso.fi/onto/$i/ /home/joelitak/vocabularies/$i/$i-skos.ttl
done
