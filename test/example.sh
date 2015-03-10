#!/bin/sh
curl -i -H "Content-type: application/json" --data-binary '{"signature":["project:ss","state:up"],"tags":["customer:scripps","test:search"],"source":"Pingdom","short-message":"OOOOOOOPS","severity":"critical"}' http://localhost:3000/api/notify

