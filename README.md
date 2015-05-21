# Recommendation Template with Batch Prediction Persistence


## Batch Prediction Persistence

This engine template is an example of how one could perform batch prediction by
leveraging `pio eval`.

```
$ pio build
...
$ pio eval org.template.recommendation.BatchEvaluation org.template.recommendation.BatchEngineParamsList 
...
[INFO] [BatchPersistableEvaluator] Writing result to disk
[INFO] [BatchPersistableEvaluator] Result can be found in batch_result          
[INFO] [CoreWorkflow$] Updating evaluation instance with result:
my.org.BatchPersistableEvaluatorResult@46761362
[INFO] [CoreWorkflow$] runEvaluation completed
```

You can find the result under `batch_result`

```
$ ls batch_result/
_SUCCESS	part-00001	part-00003	part-00005	part-00007
part-00000	part-00002	part-00004	part-00006
$ cat batch_result/part-00005
{"query":{"user":"3","num":15},"predictedResult":{"itemScores":[{"item":"4","score":-5.372277317794528},{"item":"42","score":-5.0392458119991845},{"item":"93","score":-3.0186207861585146},{"item":"54","score":-2.2179891650046386},{"item":"21","score":-2.141311818381439},{"item":"77","score":-1.8896707836711037},{"item":"5","score":-1.6837156785309997},{"item":"40","score":-1.2496127931205532},{"item":"61","score":-1.0110812515841427},{"item":"57","score":-0.9766616618987314},{"item":"53","score":-0.7878164508672993},{"item":"63","score":-0.6976154253784461},{"item":"55","score":-0.6688289619098153},{"item":"20","score":-0.16089866298419864},{"item":"85","score":0.1323610863904019}]}}
```


## PredictionIO Evaluation Module Explained

When we run `pio eval`, the first parameter is the static object for the
evaluation definition, and the second parameter is the list of `EngineParams` we
want to test. The evaluation definition contains two main components, 1. the
engine, 2. the `BaseEvaluator`. `BaseEvaluator` is responsible to evaluating the
quality of engine instances.

`pio eval` kicks start the evaluation process. For each engine params specified
in the list, PredictionIO instantiates an engine instance. For each engine
instance, `DataSource.readEval` is invoked and generate a list of training and
testing data sets. (Please refer to PIO documentation site for detailed
description). Each engine instance produces (roughly) a list of
`RDD[(Q, P, A)]`, it is the testing data which the groud truth result.

PredictionIO then combines the results from all engine instances, then send to
the `BaseEvaluator` via the `evaluateBase` method. Hence, `evaluateBase` is able
to *see* all query-predictedResult-actualResult tuples.

[BatchPersistablePrediction.scala](src/main/scala/BatchPersistablePrediction.scala) 
contains all the code change you need.


## Documentation

Please refer to http://docs.prediction.io/templates/recommendation/quickstart/
