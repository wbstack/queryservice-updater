# queryservice-updater

## 0.3.84_3.3 - 10th September 2021

- Remove heapsize + memory from runupdate
- Increase socket timeout to 5s

**0.3.84_3.2 - 10th September 2021**

- Debug log number of threads
- Use IdleConnectionEvictor
- Call con.disconnect

**0.3.84_3.1 - 10th September 2021**

- Introduce `WBSTACK_THREAD_COUNT` env var (default 10, same as before)
- Introduce `WBSTACK_WIKIBASE_SCHEME` env var (default https, same as before 3.0)
- Thus, switch from incorrect http scheme to https by default (http used locally)

**0.3.84_3.0 - 7th September 2021**

- Rewrite the Java wrapper around origional updater (extracting some things)
- Attempt to eliminate memory and thread leaks

## 0.3.6_2.2 - June 2021

- Bump gson from 2.8.6 to 2.8.7

## 0.3.6_2.1 - December 2020

- [Handle non JSON responses from getBatches API](https://github.com/wbstack/queryservice-updater/commit/b24b813b0155837cfb3a225af694101569f55301)

## 0.3.6_2.0 - November 2020

- From Github Build

## May 2020

- 0.3.6-1.7-d0.6 - No longer pass -N in our shell script
- 0.3.6-1.6-d0.6 - Output memory usage
- 0.3.6-1.4-d0.6 - Try to garbage collect more and use less memory...

## April 2020

- 0.3.6-1.3-d0.6 - Hide IOExceptions that happen when API is gone for restarts
- 0.3.6-1.2-d0.6 - Better user agent
- 0.3.6-1.1-d0.6 - Totally refactor the Java class =]
- 0.3.6-1.0-d0.6 - Only have 1 QS updater implementation again (+ reduce non needed output)
- queryservice-updater-2:0.3.6-0.1-d0.6 - Sleep on empty batch... (again)
- queryservice-updater-2:0.3.6-0.1-d0.5 - Sleep on empty batch...
- queryservice-updater-2:0.3.6-0.1-d0.4 - ENTRYPOINT
- queryservice-updater-2:0.3.6-0.1-d0.3 - Fix conceptUri case
- queryservice-updater-2:0.3.6-0.1-d0.2 - Add bash & fix Main class..
- queryservice-updater-2:0.3.6-0.1 - Initial java version test

## Legacy PHP Implementation

### January 2019

- 0.3.6-0.2-u0.6 - updater, allow lexeme namespace id

### December 2019

- 0.3.6-0.2-u0.5 - Rewrite process handling - tweaks 2
- 0.3.6-0.2-u0.4 - Rewrite process handling - tweaks 1
- 0.3.6-0.2-u0.3 - Rewrite process handling
- 0.3.6-0.2-u0.2 - output errors to stderr

### November 2019

- 0.3.6-0.1-u0.1 - First 0.3.6-0.1 qs with 0.1 of updater code
- 0.13 - make updater slightly more resilient to backend api failures (and backoff) - https://github.com/addshore/wbstack/issues/22

### October 2019

- 0.12 - WORKSHOP, updater poking
- 0.11 - THURSDAY, https for updates (TODO should be env var..?)
- 0.10 - Wednesday before Wikidatacon
- 0.1 - Initial Version
