# Generic JPA Criteria REST Library

## Overview

The Generic JPA Criteria REST Library is a powerful and flexible tool for Spring Boot applications, enabling users to perform dynamic database queries through a single REST endpoint. This library simplifies the process of querying different entities in a project by allowing clients to send a JSON request body specifying the search criteria and desired projections.

## Features

- **Single REST Endpoint**: Perform queries on any entity within your project through a single, unified endpoint.
- **Dynamic Query Building**: Leverage the power of JPA Criteria API to build queries dynamically based on the request.
- **Flexible Search Criteria**: Supports various search operations like `like`, `equals`, `isNull`, and `isNotNull`.
- **Projection Support**: Specify the fields you want in the response to avoid fetching unnecessary data.
- **Easy Integration**: Designed to seamlessly integrate with existing Spring Boot projects.

## How to Use

### Prerequisites

- Java 17 or higher
- Spring Boot 3.x
- Gradle

### Adding the Library to Your Project

You can add this library to your project as a Maven or Gradle dependency. (Note: Replace `x.y.z` with the actual version of the library.)

#### Gradle

```groovy
dependencies {
    implementation 'com.example:generic-jpa-criteria-rest:x.y.z'
}
```

### Using the Library

1. **Create Search Requests**: Send a POST request to `/search` with a JSON body defining the search criteria.

   Example Request Body:

   ```json
   {
     "where": {
       "like": {"name": "John"},
       "equalsLong": {"age": 30},
       "equalsString": {"city": "New York"},
       "isNull": ["address"],
       "isNotNull": ["phoneNumber"]
     },
     "projection": ["id", "name", "email"]
   }
   ```

3. **Handle the Response**: The server will respond with a `SearchResult` object containing the query results and the entity type.

### Records

- `Search`: Defines the search criteria and projection fields.
- `Where`: Specifies the conditions for the search, supporting various operations.
- `SearchResult`: Contains the results of the search and the type of entity queried.

## Contributing

Contributions are welcome! If you'd like to contribute, please fork the repository and use a feature branch. Pull requests are warmly welcome.

## License

This project is licensed under MIT. Feel free to use and modify it as per your needs.

---

For more information or if you encounter any issues, please open an issue in the GitHub repository.
