# Bell

A simple Clojure HTTP router. Zero dependencies. Zero macros.

## Why another HTTP router?

There are plenty of good options for HTTP routers today. However, I felt that
most of them were far more complex than necessary for my use cases. I wanted
a small router that was simple to understand and use.

This router is heavily inspired by https://github.com/matryer/way, which is a
simple HTTP router for Go applications. (There's also a dash of
https://go-chi.io/ too.)

## Example

```clojure
(ns sample.core
  (:require [bell.core :refer [GET router]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn get-id-handler [request]
  {:status 200
   :headers {}
   :body (get-in request [:path-params :id])})

(def handler
  (router
   (GET "/paths/:id" get-id-handler)))

(defn -main [& _args]
  (run-jetty handler {:port 8080 :join? false}))
```

## Concepts

Bell provides a few concepts and each has a corresponding function: `route`,
`group`, `subrouter`, and `router`. The details of each will be discussed in
turn.

A route matches an HTTP verb and pattern to a handler. If a request does not
match the route specified, the handler will return nil. This differs from
typical ring handlers, which should always return a response. However, a route
is usually not used in isolation, and the `router` function will return a ring
handler that is guaranteed to return a response. Routes are usually composed by
a router.

An example for defining a route might look like the following:

```clojure
(bell/route :get "/my/path/:id" my-path-id-handler)
```

Note that the pattern contains `:id`. This specified a path parameter named id.
When a request matches a route, the path parameters will be parsed into a map
on the request under the key `:path-params`.

Patterns may also serve as prefixes. If a pattern ends with `/` (except the root
route) or `...`, the pattern is treated as a prefix. Here are some examples. The
pattern `/api/` matches `/api/some/other/path` and the pattern
`/images/image-...` matches `/images/image-logo.png` but not
`images/logo.png`.

There are convenience methods for each of the HTTP methods (e.g., GET), and a
special one, ANY, that matches any method.

A group is a collection of routes. Groups are useful for attaching middleware to
several routes at once. It is important to note that there is not an analog to
compojure's `wrap-matched-routes`. If you want to ensure that a middleware only
applies to a matched handler, apply it to each route individually.

A group will try to apply each route in order. If a route returns nil, it is
considered unmatched, and the group will attempt the next route in the group. If
no routes match, the group will return nil.

A subrouter is a collection of routes that are mounted at a prefix path (e.g.,
`/api`). Path parameters that are part of the prefix will be parsed into the
`:path-params` map. Like a group, a subrouter will apply the request to each
route in order until one is matched.

A router is a group of routes that includes a default not-found handler. It is
guaranteed to return a response, and it is generally the top-level concept used.

## Extending

One of the key features of bell is that a handler that returns nil indicates
that a particular route has not been matched and that bell should attempt to
match against the next route in its sequence. You may use this feature to
write middlewares that return nil to force bell to attempt matching against
another route.

One potential example would be implementing a middleware that ensures that path
parameters conform to a spec. The middleware would be applied to a handler. It
would pull out the path parameters and validate them against a spec. If the
parameters are valid, the middleware will pass the request on to the handler.
However, if the parameters do not conform, the middleware may return nil to
indicate to bell that the route was not matched. Bell will then move on to the
next route.

```clojure
(bell/GET "/my/path/:id" (wrap-ensure-spec my-handler [:map [:id :uuid]]))
```

## Design Decisions

- Simple > fast. I wanted to build something small that could be read and
  understood in its entirety in 30min or less. I do trade off some speed to
  maintain simplicity. That does not mean that bell is slow, but that it is not
  as fast as some other routers. I believe bell is suited for most use cases.

- No macros. Macros are confusing to read, write, and debug. They should be
  used sparingly. They are particularly difficult for newcomers to understand.
  Ring provides a great model of composing functions, and bell builds on this
  approach.

- No default data representation. Bell tries to be as unopinionated as possible.
  It is a pretty straightforward endeavor to write a function that parses
  whatever data structure you like and transforms it into a router based on
  bell's functions.
