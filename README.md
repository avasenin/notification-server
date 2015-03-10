Notification Server
==================

Notification Server collects all alerts from monitoring systems like Nagios or Pingdom and provides powerful system for routing alerts to operation team by sms, email or pushover.

![Events](https://dl.dropboxusercontent.com/u/11486892/nns.png)

#### Features

* Personal schedules
* Personal filters
* Tagged events
* G+ OAuth
* SMS, email and Pushover support

#### Status

Used for many years in Echo (JackNyfe Inc) to deliver notifications to operations and engineers.

#### Localhost development

You need to get Datomic free licence to have ability download Datomic's peer library. You should register on https://my.datomic.com/ and recieve download key. You will see instruction after registration how to do add datomic repository to maven.

We use memory Datomic's backend to avoid Datomic server installation for local development.

Clone repo and execute `lein run` to launch Notification Server

##### Troubleshooting

Note: Datomic does not handle MacBook sleep mode well. If you are getting

        Error communicating with HOST localhost

try restarting the transactor.

##### License

Copyright ©2013-2015 JackNyfe Inc

Distributed under the Eclipse Public License, the same as Clojure.
