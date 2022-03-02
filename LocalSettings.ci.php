<?

$wgServer = 'https://wikibase.svc';

$wgWBRepoSettings['entitySources'] = [
  'local' => 
  [
    'entityNamespaces' => 
    [
      'item' => '120/main',
      'property' => '122/main',
    ],
    'repoDatabase' => false,
    'baseUri' => 'https://wikibase.svc/entity/',
    'rdfNodeNamespacePrefix' => 'wd',
    'rdfPredicateNamespacePrefix' => '',
    'interwikiPrefix' => '',
  ],
];