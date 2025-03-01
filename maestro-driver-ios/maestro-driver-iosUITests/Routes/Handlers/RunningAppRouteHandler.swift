import FlyingFox
import XCTest
import os

class RunningAppRouteHandler: RouteHandler {
    private static let springboardBundleId = "com.apple.springboard"
    
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier!, category: "RunningAppRouteHandler")
    
    func handle(request: HTTPRequest) async throws -> HTTPResponse {
        let decoder = JSONDecoder()
        
        guard let requestBody = try? decoder.decode(RunningAppRequest.self, from: request.body) else {
            let errorData = handleError(message: "incorrect request body provided")
            return HTTPResponse(statusCode: HTTPStatusCode.badRequest, body: errorData)
        }
        
        let runningAppId = requestBody.appIds.first { appId in
            let app = XCUIApplication(bundleIdentifier: appId)
            
            return app.state == .runningForeground
        }
        
        let response = ["runningAppBundleId": runningAppId ?? RunningAppRouteHandler.springboardBundleId]
        
        guard let responseData = try? JSONSerialization.data(
            withJSONObject: response,
            options: .prettyPrinted
        ) else {
            let errorData = handleError(message: "serialization of runningAppBundleId failed")
            return HTTPResponse(statusCode: HTTPStatusCode.badRequest, body: errorData)
        }
        
        return HTTPResponse(statusCode: .ok, body: responseData)
    }
    
    private func handleError(message: String) -> Data {
        logger.error("Failed to get running app bundle id - \(message)")
        let jsonString = """
         { "errorMessage" : \(message) }
        """
        return Data(jsonString.utf8)
    }
}
