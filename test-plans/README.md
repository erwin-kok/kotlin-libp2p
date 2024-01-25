# test-plans test implementation

This folder contains the implementation for the test-plans interoperability tests.
See: [test-plans](https://github.com/libp2p/test-plans)

In order to build the Docker image locally, do (from the repository root):

```bash
docker buildx build --load -t kotlin-libp2p-head -f test-plans/Dockerfile .
```