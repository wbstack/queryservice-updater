'use strict';
const wbEdit = require( 'wikibase-edit' )( require( '../wikibase-edit.config' ) );


describe( 'Fillerup', () => {

	it( 'Should fill the wikibase', async () => {

		while( true ) {
			const { entity } = await wbEdit.entity.create({
				type: 'item',
				labels: {
					'en': new Date().toISOString()
				},
				descriptions: {
					'en': new Date().toDateString() + new Date().toISOString()
				}
			})
			console.log('created item id', entity.id)
		}

	} );

} );
