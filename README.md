Ovonpar Stock Ticker
===================

Ovonpar Stock Ticker is a simple web application that renders services to display the best bids
and offers (BBOs) and latest trades in [Parity Trading System][].

  [Parity Trading System]: https://github.com/paritytrading/parity

Application implements REST Api for order management


Build
-----

Ovonpar system(modified Parity system) must be running.

[parity-system] must be aded to hosts and point to machine with Parity system process running

Run sbt run to start the applicattion. 

To create Docker image run sbt docker:publish

License
-------

Ovonpar Stock Ticker is released under the Apache License, Version 2.0. See
`LICENSE` for details.
