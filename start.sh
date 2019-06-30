usage(){
    echo "Usage: sh start.sh status | start | stop | restart | refresh | package | build | keystore" 
}

status(){
    PIDS=`ps -ef | grep java | grep "light4j-3.0.1.jar" |awk '{print $2}'`

	if [ -z "$PIDS" ]; then
	    echo "light4j is not running!"
	else
		echo -e "Stopping light4j ..."
		for PID in $PIDS ; do
		    echo "light4j has pid: $PID!"
		done
	fi
}

stop(){
    PIDS=`ps -ef | grep java | grep "light4j-3.0.1.jar" |awk '{print $2}'`

	if [ -z "$PIDS" ]; then
	    echo "light4j is not running!"
	else
		echo -e "Stopping light4j ..."
		for PID in $PIDS ; do
			echo -e "kill $PID"
		    kill $PID > /dev/null 2>&1
		done
	fi
}

clean(){
    echo "clean light4j ..."
	mvn clean
}

jar(){
	echo "compile light4j ..."
	mvn compile jar:jar
}

dependency(){
	echo "copy dependencies ..."
	mvn dependency:copy-dependencies -DoutputDirectory=target
}

start(){
	echo "starting light4j ..."
	JVM_OPS="-server -Xmx768M -XX:MaxPermSize=256m"
	#setsid java $JVM_OPS -Dlight4j.directory=/soft/softwares/library/ -Dlogserver -jar target/light4j-3.0.1.jar >> /dev/null 2>&1 &
	setsid java $JVM_OPS -Dlight4j.directory=/soft/softwares/library/ -Dlogserver -cp target/light4j-3.0.1.jar com.xlongwei.light4j.Servers >> /dev/null 2>&1 &
	#echo "starting light4j https ..."
	#env enableHttps=true setsid java $JVM_OPS -Dlight4j.directory=/soft/softwares/library/ -Dlogserver -jar target/light4j-3.0.1.jar >> /dev/null 2>&1 &
}

keystore(){
	dir=src/main/resources/config
	if [ $# -gt 1 ]; then 
	    cert=$dir
	else
	    cert=/soft/cert
	fi
	openssl pkcs12 -export -in $cert/xlongwei.pem -inkey $cert/xlongwei.key -name xlongwei.com -out $cert/xlongwei.p12
	keytool -delete -alias xlongwei.com -keystore $dir/server.keystore -storepass password
	keytool -importkeystore -deststorepass password -destkeystore $dir/server.keystore -srckeystore $cert/xlongwei.p12 -srcstoretype PKCS12
}

if [ $# -eq 0 ]; then 
    usage && stop && start
else
	case $1 in
	status) status ;;
	start) start ;;
	stop) stop ;;
	build) jar ;;
	package) jar && dependency ;;
	restart) stop && start ;;
	refresh) stop && clean && jar && dependency && start ;;
	keystore) keystore $@;;
	*) usage ;;
	esac
fi