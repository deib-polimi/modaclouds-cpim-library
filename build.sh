mvn package -Dmaven.javadoc.skip=true && echo '' && cp target/cpim-library-1.0-SNAPSHOT.jar  ../kundera-test/lib/  && echo '.jar copied to lib/' && cp target/cpim-library-1.0-SNAPSHOT.jar  ../kundera-test/war/WEB-INF/lib/ && echo '.jar copied to war/WEB-INF/lib/'