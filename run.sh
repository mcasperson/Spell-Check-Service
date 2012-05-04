#!/bin/bash

#STOMP_SERVER=localhost
#REST_SERVER=http://localhost:8080/TopicIndex/seam/resource/rest

STOMP_SERVER=skynet.usersys.redhat.com
REST_SERVER=http://skynet.usersys.redhat.com:8080/TopicIndex/seam/resource/rest
PORT=61613
USER=guest
PASS=guest
QUEUE=jms.queue.SkynetDocbookBuildQueue
MAINCLASS=com.redhat.topicindex.syntaxchecker.Main
QUERY=query;

java \
-DtopicIndex.stompMessageServer=${STOMP_SERVER} \
-DtopicIndex.stompMessageServerPort=${PORT} \
-DtopicIndex.stompMessageServerUser=${USER} \
-DtopicIndex.stompMessageServerPass=${PASS} \
-DtopicIndex.stompMessageServerQueue=${QUEUE} \
-DtopicIndex.skynetServer=${REST_SERVER} \
-DtopicIndex.spellCheckQuery=${QUERY} \
-jar spellchecker-service.jar

