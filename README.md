# geas

Simple metrics instrumentation in Clojure for core.async workflow

## Usage

For now , a simple example with `clj-http`:

```clojure
(let [perf-chan (as/chan 50)
	  resp-chan (geas/promise-chan {:registry registry ;; a metrics registry, optional
									:perf-chan perf-chan
									;; metric name in clj-metrics and data in perf-chan
									:metric ["my-app" "my-call"]})]

  (client/get "http://example.com/"
			  {:async? true
			   :accept :json
			   :socket-timeout 500
			   :conn-timeout 500}
			  (fn [resp]
				(try
				  (as/put! resp-chan resp)
				  (catch Exception e
					(as/put! resp-chan e))))
			  (fn [e]
				(as/put! resp-chan e))))
```

## License

Copyright © 2017 Oscaro.com

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
