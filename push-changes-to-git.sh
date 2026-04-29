#!/bin/bash

git pull
# Roll the history forwards
cp kokoUriVastaavuudet-test.txt kokoUriVastaavuudet.txt

git add *.java lib/*.jar
git add kokoUriVastaavuudet.txt
git add kokoaja.log
git add conf/*


git commit -m "KOKO päivitetty"
git push
