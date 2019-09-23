#!/bin/sh
lein clean
shadow-cljs release app && lein uberjar
