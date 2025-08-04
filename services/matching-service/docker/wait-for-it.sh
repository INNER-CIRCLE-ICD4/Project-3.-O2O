#!/usr/bin/env bash
# wait-for-it.sh: Wait for a service to be ready

set -e

host="$1"
shift
cmd="$@"

until nc -z "$host"; do
  >&2 echo "Waiting for $host to be ready..."
  sleep 1
done

>&2 echo "$host is ready!"
exec $cmd