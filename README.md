# Design patterns

## Concurrency patterns

[Join calculus](design_patterns/join/) - Provide a way to abstract the complexity of concurrent environment and to allow scalability by focusing on execution of a chord between messages atomically consumed from a group of channels.

[Proactor](design_patterns/proactor/) - Allow event-driven applications to process requests in an efficient asynchronous way by splitting them into long-duration asynchronous operations and completion handlers that process the results of the associated operations.


[Reactor](design_patterns/reactor/) - Process requests received concurrently from one or more inputs by a service that demultiplexes the incoming requests and dispatches them synchronously to the associated request handlers.
