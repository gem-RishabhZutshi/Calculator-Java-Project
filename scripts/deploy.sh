#!/usr/bin/env bash

NAME=$1
AWS_ACCOUNT=${2:-593793025658}
REGION=ap-south-1
ECR_URL="$AWS_ACCOUNT.dkr.ecr.$REGION.amazonaws.com"
CLUSTER="arn:aws:ecs:$REGION:$AWS_ACCOUNT:cluster/Demo-VPC-Backend-APP"

COMMIT_HASH=`date +%Y%m%d%H%M%S`
echo $COMMIT_HASH

echo "Building image: $NAME:$COMMIT_HASH"
docker build --rm -t $NAME:$COMMIT_HASH .

aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_URL

# tag and push image using latest
docker tag $NAME:$COMMIT_HASH $ECR_URL/$NAME:$COMMIT_HASH
docker push $ECR_URL/$NAME:$COMMIT_HASH

aws ecs update-service --cluster ${CLUSTER} --region ${REGION} --service $NAME-Service --force-new-deployment









