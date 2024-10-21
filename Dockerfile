#
# Copyright © 2020 CodeOnce Software (https://www.codeonce.fr/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM openjdk:17-ea-10-jdk

VOLUME /tmp

RUN echo '[DOCKER] Building in progress...'

ARG JAR_FILE
ADD target/${JAR_FILE} /usr/share/codeonce/app.jar

ENTRYPOINT [ "java", "-jar", "/usr/share/codeonce/app.jar" ]

EXPOSE 8090

RUN echo '[DOCKER] Build completed!'