# mekanikmaskin

A Clojure based system designed to test people in various forms of knowledge about a course in mechanics for KTH.

## ALPHA!

this is to be used by no-one ATM, code online just for working

## Usage

Call the (dev) function to load the `dev` namespace.

```clj
(dev)
```

It contains several useful functions. To see a list
of some of these functions, type:

```clj
(tools-help)
```

To begin working on an application, execute:

```clj
(start)
```

and then visit `http://localhost:3000`.

```clj
(watch :development)
```
(require 'dev)
(in-ns 'dev)
(start)

then change in service.clj

## Development
Please check out the issues page: https://github.com/claj/mekanikmaskin/issues for scrumboard galore.

There are [Midje](https://github.com/marick/Midje) testcases evolving.

## Similar applications

### Flash cards
[Anki (flash cards)](http://ankisrs.net/)

Outstanding [Wired article about Super Memo](http://www.wired.com/medtech/health/magazine/16-05/ff_wozniak?currentPage=all)

Wikipedas page about [Spaced Repetiton](http://en.wikipedia.org/wiki/Spaced_repetition)

KTH Social - the university's mainly closed open course ware system.

### Vägverkets körkortsprovstest
Swedish Driving Licence theory test, 70 questions, of which 5 are test questions, correct answers to at least 52 of 65 theory questions in 50 minutes.

### All online courses
Khan?

### Programming projects and tools of relevance
[4clojure](https://github.com/4clojure/4clojure) - "fill in the gaps in clojure source code" - statistics, some web things. [MongoDB](https://github.com/aboekhoff/congomongo.git) best practice.

[pedestal.io](http://pedestal.io) - A sane and simple/demanding way to do make webapps from Clojure/core-wizards. Stable alpha.

Incanter - Mathematics library. Latex for rendering of formulas. Chi2-testing.

Datomic - A sane transactional relational database.

## Domain (sketch)

<img src="https://raw.github.com/claj/mekanikmaskin/master/doc/proto-domain.png"
 alt="a hand-drawn picture of the domain with user, task, goal, teacher" title="Sketchy domain" align="center" />

## Aim

The possibility to log in with KTH-ID and click through the tasks until you get it.

### The problems that need the domain established but are insanely intresting later on

- Machine learning to select better tasks by relevance-like measures in a way that heads for gaining badges. Apriori combined with Steepest descent?

- Compare the "students" "usable knowledge" with a computer based approach (supervised teaching) visavi a lecture-exercises-exam method (unsupervised teaching).

- ...insert various rant about the low innovation rate in teaching practices in academia...

## License

Copyright © 2013 Linus Ericsson

Distributed under the Eclipse Public License, the same as Clojure.
