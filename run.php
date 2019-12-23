<?php

require_once __DIR__ . '/vendor/autoload.php';

$pidFile = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'qs-updater-pid';

// Convenience function to get an env var or die.
$getEnvOrFail = function( $name ) {
	$e = getenv( $name );
	if ( $e === false ) {
		die( "ERROR: env {$name} not defined" );
	}
	return $e;
};

$apiEndpointGetBatches = 'http://' . getenv( 'PLATFORM_API_BACKEND_HOST' ) . '/backend/qs/getBatches';
$apiEndpointMarkBatchesDone = 'http://' . getenv( 'PLATFORM_API_BACKEND_HOST' ) . '/backend/qs/markDone';
$apiEndpointMarkBatchesFailed = 'http://' . getenv( 'PLATFORM_API_BACKEND_HOST' ) . '/backend/qs/markFailed';

$isLocalHostOnly = getenv('IS_LOCALHOST_ONLY');

echo "Starting loop\n";
// TODO perhaps this shouldn't loop forever? and instead it should sleep based on env var and calculation and restart php?
// TODO also perhaps this should be in something lighter than php..
while ( true ) {
	$startPass = time();
	$batches = [];
	$lastBackendApiRequestFailed = false;

	// Check a pid file from a perhaps previously killed php process?
	if( file_exists( $pidFile ) ) {
	    $pidFileContent = file_get_contents( $pidFile );
	    if(file_exists("/proc/$pidFileContent")) {
	        // Process still exists
            fwrite(STDERR, "pidfile: process $pidFileContent still appears to be running. Sleeping for 10 and retrying.." . PHP_EOL);
            sleep(10);
            continue;
        } else {
	        // File exists but was not cleaned up correctly.
	        unlink($pidFile);
            fwrite(STDERR, "pidfile: was not cleaned up for process $pidFileContent" . PHP_EOL);
        }
    }

	// Get batches
    //TODO UAs and access tokens?
    try{
        $result = Requests::get($apiEndpointGetBatches);
        if(!$result->success) {
            throw new Requests_Exception ( 'Result did not return a success:' . json_encode( $result ), 'custom' );
        }
        $batches = json_decode( $result->body );
    }
    catch ( Requests_Exception $reqException ) {
        $batches = [];
        $lastBackendApiRequestFailed = true;
        fwrite(STDERR, "API call failed with Requests_Exception: " . $reqException->getType() . ': ' . $reqException->getMessage() . PHP_EOL);
    }

    $successBatches = [];
    $failBatches = [];

    // Try to write batches to backend SPARQL endpoints
    foreach( $batches as $batch ) {
        // TODO maybe the URI can actually be specified here too? instead of always using domain. May be useful in the case of site renames...
        $command = '/wdqs/runUpdate.sh';
        $command .= ' -h ' . escapeshellcmd( 'http://' . $batch->wiki->wiki_queryservice_namespace->backend );
        $command .= ' -n ' . escapeshellcmd( $batch->wiki->wiki_queryservice_namespace->namespace );
        $command .= ' -N '; // -N option allows Updater script to run in parallel with regular Updater service without port conflict.
        $command .= ' --';
        // TODO is this request counting as external traffic? if so, it shouldn't be...
        // TODO need the updater to pass a HOST header and use the internal host =]
        if($isLocalHostOnly) {
            // Internal host for docker-compose usage...
            // NOTE: this will ONLY work for the first wiki created.
            // TODO: figure out if I can make this work nicely for all wikis....
            $command .= ' --wikibaseHost ' . escapeshellcmd( 'mediawiki' );
            $command .= ' --wikibaseScheme http';
        } else {
            $command .= ' --wikibaseHost ' . escapeshellcmd( $batch->wiki->domain );
            $command .= ' --wikibaseScheme https';
        }
        $command .= ' --conceptUri http://' . escapeshellcmd( $batch->wiki->domain );
        $command .= ' --entityNamespaces ' . escapeshellcmd( '120,122' );
        $command .= ' --ids ' . escapeshellcmd( $batch->entityIds );
        //Note: --verbose added to the end will output verbose output

        // TODO detect failures and do not update the lasteventloggingid in blazegraph
        echo "Proc cmd $command \n";

        $descriptorSpec = array(
            //0 => ["pipe", "r"],  // stdin is a pipe that the child will read from
            1 => ["pipe", "w"],  // stdout is a pipe that the child will write to
            2 => ["pipe", "w"] // stderr is a pipe that the child will write to
        );
        $process = proc_open( $command, $descriptorSpec, $pipes );
        if (is_resource($process)) {

            $status = proc_get_status($process);
            $pid = $status["pid"];
            file_put_contents( $pidFile, $pid );
            echo "Proc $pid.\n";

            // Continually write the output of the command to the logs (both stderr and stdout)
            $continueLoop = true;
            while ($continueLoop) {
                sleep(0.2);
                // Check for eof inside the loop so the last lines are still output
                $continueLoop = !feof($pipes[1]) && !feof($pipes[2]);
                foreach($pipes as $key => $pipe) {
                    $line = fread($pipe, 2048);
                    if($line) {
                        // Write to error or out depending on which pipe it came in from.
                        if($key == 1) {
                            // TODO filter out some regular output lines we know suck?
                            fwrite(STDOUT, trim($line) . PHP_EOL);
                        }
                        if($key == 2) {
                            fwrite(STDERR, trim($line) . PHP_EOL);
                        }
                    }
                }
            }

            fclose($pipes[1]);
            fclose($pipes[2]);

            $returnValue = proc_close($process);

            echo "Proc $pid return $returnValue\n";
            unlink( $pidFile );

            if($returnValue !== 0){
                $failBatches[] = $batch->id;
            } else {
                $successBatches[] = $batch->id;
            }

        } else {
            $failBatches[] = $batch->id;
            die("ERROR, failed to start JAVA process");
        }
    }

    // Mark batches failed
    if(!empty($failBatches)) {
        $result = Requests::post($apiEndpointMarkBatchesFailed . '?' . http_build_query(['batches' => implode(',', $successBatches)]));
        if(!$result->success) {
            var_dump($result->success);die();
            die("ERROR, failed to markBatchesFailed using API");
        }
    }


    // TODO it would be good to re enable marking batches as done....
	// Mark batches done
//    if(!empty($successBatches)) {
//        $result = Requests::post($apiEndpointMarkBatchesDone . '?' . http_build_query(['batches' => implode(',', $successBatches)]));
//        if(!$result->success) {
//            var_dump($result->success);die();
//            die("ERROR, failed to markBatchesDone using API");
//        }
//    }

    // Wait some time?
	$endPass = time();
	$timeRunning = $endPass - $startPass;
	$configPassTime = $getEnvOrFail( 'PASS_TIME' );
	if ( $timeRunning < $configPassTime ) {
	    if( $lastBackendApiRequestFailed ) {
            $sleepFor = $configPassTime + 5;
            echo "Sleep {$sleepFor}s. Passtime {$configPassTime}+5\n";
        } else {
            $sleepFor = $configPassTime - $timeRunning;
            echo "Sleep {$sleepFor}s. Passtime {$configPassTime}\n";
        }
        sleep( $sleepFor );
    }

}
