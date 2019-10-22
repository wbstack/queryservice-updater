<?php

require_once __DIR__ . '/vendor/autoload.php';

// Convenience function to get an env var or die.
$getEnvOrFail = function( $name ) {
	$e = getenv( $name );
	if ( $e === false ) {
		die( "ERROR: env {$name} not defined" );
	}
	return $e;
};

$apiEndpointGetBatches = 'http://api:80/backend/qs/getBatches';
$apiEndpointMarkBatchesDone = 'http://api:80/backend/qs/markDone';

$isLocalHostOnly = getenv('IS_LOCALHOST_ONLY');

echo "Starting loop\n";
// TODO perhaps this shouldn't loop forever? and instead it should sleep based on env var and calculation and restart php?
// TODO also perhaps this should be in something lighter than php..
while ( true ) {
	$startPass = time();

	// Get batches
    //TODO UAs and access tokens?
    $result = Requests::get($apiEndpointGetBatches);
    if(!$result->success) {
        die("ERROR, failed to getBatches from API");
    }
    $batches = json_decode( $result->body );
    $successBatches = [];

    // Try to write batches to backend SPARQL endpoints
    foreach( $batches as $batch ) {
        // TODO maybe the URI can actually be specified here too? instead of always using domain. May be useful in the case of site renames...
        echo "runUpdate.sh for {$batch->wiki->domain} on {$batch->wiki->wiki_queryservice_namespace->backend} in {$batch->wiki->wiki_queryservice_namespace->namespace} with {$batch->entityIds}\n";
        $command = '/wdqs/runUpdate.sh';
        $command .= ' -h ' . escapeshellcmd( 'http://' . $batch->wiki->wiki_queryservice_namespace->backend );
        $command .= ' -n ' . escapeshellcmd( $batch->wiki->wiki_queryservice_namespace->namespace );
        $command .= ' -N '; // -N option allows Updater script to run in parallel with regular Updater service without port conflict.
        $command .= ' --';
        // TODO is this request counting as external traffic? if so, it shouldn't be...
        if($isLocalHostOnly) {
            // Internal host for docker-compose usage...
            // NOTE: this will ONLY work for the first wiki created.
            // TODO: figure out if I can make this work nicely for all wikis....
            $command .= ' --wikibaseHost ' . escapeshellcmd( 'mediawiki' );
        } else {
            $command .= ' --wikibaseHost ' . escapeshellcmd( $batch->wiki->domain );
        }
        $command .= ' --conceptUri http://' . escapeshellcmd( $batch->wiki->domain );
        $command .= ' --wikibaseScheme http';
        $command .= ' --entityNamespaces ' . escapeshellcmd( '120,122' );
        $command .= ' --ids ' . escapeshellcmd( $batch->entityIds );
        //Note: --verbose added to the end will output verbose output

        // TODO detect failures and do not update the lasteventloggingid in blazegraph
        echo $command."\n";
        exec( $command, $execOutput, $execReturn );
        foreach( $execOutput as $outputLine ) {
            echo $outputLine . "\n";
        }
        if( $execReturn !== 0 ) {
            echo "exec return non 0 exit code: " . $execReturn . "\n";
        } else {
            $successBatches[] = $batch->id;
        }
    }


	// Mark batches done
    if(!empty($successBatches)) {
        $result = Requests::post($apiEndpointMarkBatchesDone . '?' . http_build_query(['batches' => implode(',', $successBatches)]));
        if(!$result->success) {
            var_dump($result->success);die();
            die("ERROR, failed to markBatchesDone using API");
        }
    }

    // Wait some time?
	$endPass = time();
	$timeRunning = $endPass - $startPass;
	$configPassTime = $getEnvOrFail( 'PASS_TIME' );
	if ( $timeRunning < $configPassTime ) {
		$sleepFor = $configPassTime - $timeRunning;
		echo "Sleeping for {$sleepFor} seconds. Config pass time {$configPassTime}\n";
		sleep( $sleepFor );
	}

}
