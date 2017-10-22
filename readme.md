KActorAnnotations
=================

This is the first shot at implementing an annotation layer ontop of kotlin coroutine actors

The aim is to get a comfotable programming model on actors as described in the
[Task Concurrency Manifesto](https://gist.github.com/lattner/31ed37682ef1576b16bca1432ea9f782#part-2-actors-eliminating-shared-mutable-state)
by leaving the boilerplate to an annotation processor.

As this is a 'first shot', there are a few things left to do. Including the following:
* architectural cleanup
* copying modifiers of the original class and it's functions
* corner cases?
* unit tests
* documentation


KActor annotation
----------------
The KActor annotation applies to classes and generates a new class with the suffix `Actor`
This *Actor class starts an actor. And for each function in the original class an actor function will be generated, that 
sends a coresponding message to the actor which in turn will execute the original function within it's context.

```
  